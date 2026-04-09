package com.matter_moulder.lyumixdiscordauth.auth;

import com.matter_moulder.lyumixdiscordauth.Server;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class AuthStateManager {
    // state → entry (for OAuth callback)
    private static final ConcurrentHashMap<String, PendingStateEntry> pendingStates = new ConcurrentHashMap<>();

    // playerUuid → scheduled timeout (for probe)
    private static final Map<UUID, ScheduledFuture<?>> pendingProbes = new ConcurrentHashMap<>();

    // playerUuid → auth stage
    private static final ConcurrentHashMap<UUID, AuthStage> playerStages = new ConcurrentHashMap<>();

    // playerUuid -> one-time probe nonce used to bind refresh-token attempts to a live auth session.
    private static final ConcurrentHashMap<UUID, String> pendingNonces = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long STATE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private record PendingStateEntry(UUID playerUuid, long createdAtMillis) {}

    static {
        scheduler.scheduleAtFixedRate(AuthStateManager::cleanupExpiredStates, 5, 5, TimeUnit.MINUTES);
    }

    public enum AuthStage {
        DETECTING_MOD,
        WAITING_OAUTH_GUI,    // mod detected, waiting for user to complete OAuth flow in external browser
        WAITING_DISCORD_LINK, // mod is absent or failed to detect, waiting for user to link account via Discord
        AUTHENTICATED
    }

    public static void setStage(UUID playerUuid, AuthStage stage) {
        playerStages.put(playerUuid, stage);
    }

    // ── Probe ────────────────────────────────────────────────────

    public static void startProbe(ServerPlayerEntity player, int timeoutSeconds, Runnable onTimeout) {
        UUID uuid = player.getUuid();
        playerStages.put(uuid, AuthStage.DETECTING_MOD);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (pendingProbes.remove(uuid) != null) {
                pendingNonces.remove(uuid);
                playerStages.put(uuid, AuthStage.WAITING_DISCORD_LINK);
                // Running on main thread to avoid potential race conditions with player disconnecting right after timeout and cleanup() removing the stage.
                Server.getServer().execute(onTimeout);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        pendingProbes.put(uuid, future);
    }

    public static void onModDetected(ServerPlayerEntity player, Runnable onConfirmed) {
        UUID uuid = player.getUuid();
        ScheduledFuture<?> future = pendingProbes.remove(uuid);
        if (future == null) return; // probe already timed out, ignore late mod detection

        future.cancel(false);
        playerStages.put(uuid, AuthStage.WAITING_OAUTH_GUI);
        Server.getServer().execute(onConfirmed);
    }

    // ── OAuth state ───────────────────────────────────────────────

    public static String generateState(UUID playerUuid) {
        String state = UUID.randomUUID().toString();
        // Store creation time so stale OAuth states can be evicted.
        pendingStates.put(state, new PendingStateEntry(playerUuid, System.currentTimeMillis()));
        return state;
    }

    public static UUID getPlayerByState(String state) {
        PendingStateEntry entry = pendingStates.remove(state); // remove — одноразовый
        if (entry == null) {
            return null;
        }
        if (entry.createdAtMillis() + STATE_TTL_MILLIS < System.currentTimeMillis()) {
            return null;
        }
        return entry.playerUuid();
    }

    public static void storePendingNonce(UUID playerUuid, String nonce) {
        if (nonce == null || nonce.isBlank()) {
            pendingNonces.remove(playerUuid);
            return;
        }
        pendingNonces.put(playerUuid, nonce);
    }

    public static String getPendingNonce(UUID playerUuid) {
        return pendingNonces.get(playerUuid);
    }

    public static void removePendingNonce(UUID playerUuid) {
        pendingNonces.remove(playerUuid);
    }

    // ── Auth completion ───────────────────────────────────────────

    public static boolean completeAuth(UUID playerUuid) {
        while (true) {
            AuthStage current = playerStages.get(playerUuid);
            if (current == null || current == AuthStage.AUTHENTICATED) {
                return false;
            }

            // CAS loop: retry while state is changing until one caller wins auth completion.
            if (playerStages.replace(playerUuid, current, AuthStage.AUTHENTICATED)) {
                break;
            }
        }

        ScheduledFuture<?> future = pendingProbes.remove(playerUuid);
        if (future != null) future.cancel(false);
        pendingStates.values().removeIf(entry -> entry.playerUuid().equals(playerUuid));
        pendingNonces.remove(playerUuid);

        return true;
    }


    public static boolean isPendingAuth(UUID playerUuid) {
        AuthStage stage = playerStages.get(playerUuid);
        return stage != null && stage != AuthStage.AUTHENTICATED;
    }

    public static AuthStage getCurrentStage(UUID playerUuid) {
        return playerStages.get(playerUuid);
    }

    // ── Cleanup ───────────────────────────────────────────────────

    public static void cleanup(UUID playerUuid) {
        ScheduledFuture<?> future = pendingProbes.remove(playerUuid);
        if (future != null) future.cancel(false);

        playerStages.remove(playerUuid);
        pendingStates.values().removeIf(entry -> entry.playerUuid().equals(playerUuid));
        pendingNonces.remove(playerUuid);
        PlayerAuthManager.clearLastAcceptedPacket(playerUuid);
    }

    private static void cleanupExpiredStates() {
        long now = System.currentTimeMillis();
        pendingStates.entrySet().removeIf(entry -> entry.getValue().createdAtMillis() + STATE_TTL_MILLIS < now);
    }
}
