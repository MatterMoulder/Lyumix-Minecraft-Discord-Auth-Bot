package com.matter_moulder.lyumixdiscordauth.auth;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.discord.DiscordBotManager;
import com.matter_moulder.lyumixdiscordauth.discord.RoleCheckService;
import com.matter_moulder.lyumixdiscordauth.oauth.DiscordOauthService;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Coordinates all auth paths (auto-login, OAuth, Discord notify) in one place.
 */
public class AuthOrchestrator {
    private static final long MILLIS_PER_HOUR = 60 * 60 * 1000;
    private static final int PROBE_TIMEOUT_SECONDS = 3;

    private AuthOrchestrator() {
    }

    public enum DiscordApprovalResult {
        APPROVED,
        NOT_PENDING,
        ROLE_DENIED
    }

    public enum AuthFlowMode {
        OAUTH_NOTIFY_FALLBACK,
        OAUTH_ONLY,
        NOTIFY_ONLY
    }

    public static void startAuth(ServerPlayerEntity player) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            PlayerAuthManager.kickPlayer(player);
            return;
        }

        Object playerId = db.getPlayerIdByName(player.getName().getString());
        if (playerId != null && isAutoLoginAllowed(db, playerId, player.getIp())) {
            completeAuth(player, playerId, false);
            return;
        }

        AuthFlowMode mode = resolveAuthFlowMode();
        switch (mode) {
            case NOTIFY_ONLY -> startNotifyFlow(player, playerId);
            case OAUTH_ONLY -> startOAuthProbe(player, () -> onOAuthOnlyProbeTimeout(player));
            case OAUTH_NOTIFY_FALLBACK -> startOAuthProbe(player, () -> startNotifyFlow(player, playerId));
        }
    }

    public static void onProbeDetected(ServerPlayerEntity player, String refreshToken, String clientBindingHash, String clientMachineIdHash, String clientServerAddress) {
        if (isBlank(clientBindingHash) || isBlank(clientMachineIdHash) || isBlank(clientServerAddress)) {
            player.sendMessage(Text.literal("Client mod outdated or tampered").formatted(Formatting.RED), false);
            PlayerAuthManager.kickPlayer(player);
            return;
        }

        AuthStateManager.onModDetected(player, () -> {
            DatabaseManager db = Server.getDatabase();
            if (db != null) {
                Object playerId = db.getPlayerIdByName(player.getName().getString());
                if (playerId != null && tryInstantRefreshAuth(player, playerId, refreshToken, clientBindingHash, clientMachineIdHash, clientServerAddress, () -> beginOAuthFlow(player))) {
                    return;
                }
            }

            beginOAuthFlow(player);
        });
    }

    public static void onOAuthCallback(UUID playerUuid, String discordUserId) {
        Server.getServer().execute(() -> {
            ServerPlayerEntity player = Server.getServer().getPlayerManager().getPlayer(playerUuid);
            if (player == null || !PlayerAuthManager.isPendingAuth(player)) {
                return;
            }

            DatabaseManager db = Server.getDatabase();
            if (db == null) {
                PlayerAuthManager.kickPlayer(player);
                return;
            }

            Object playerId = db.getPlayerIdByName(player.getName().getString());
            if (playerId == null) {
                try {
                    db.savePlayerData(player.getName().getString(), player.getIp(), discordUserId);
                } catch (RuntimeException e) {
                    Server.getPluginLogger().warn("Failed to create player record during OAuth registration for {}",
                            Utils.sanitizeLog(player.getName().getString()), e);
                    kickRegistrationUnavailable(player);
                    return;
                }
                playerId = db.getPlayerIdByName(player.getName().getString());
                if (playerId == null) {
                    Server.getPluginLogger().warn("Failed to resolve player record after OAuth registration for {}",
                            Utils.sanitizeLog(player.getName().getString()));
                    kickRegistrationUnavailable(player);
                    return;
                }
            }

            String linkedDiscordId = db.getPlayerDiscordId(playerId);
            if (linkedDiscordId == null || linkedDiscordId.isBlank()) {
                db.setPlayerDiscordId(playerId, discordUserId);
                linkedDiscordId = discordUserId;
            }

            if (!linkedDiscordId.equals(discordUserId)) {
                player.sendMessage(Text.literal(ConfigManager.msg().auth.alreadyLinkedToAnotherDiscord).formatted(Formatting.RED), false);
                PlayerAuthManager.kickPlayerWithMessage(player, ConfigManager.msg().auth.alreadyLinkedToAnotherDiscord);
                return;
            }

            player.sendMessage(Text.literal(ConfigManager.msg().auth.oauthVerificationInProgress).formatted(Formatting.YELLOW), false);

            UUID playerUuidFinal = player.getUuid();
            Object resolvedPlayerId = playerId;
            RoleCheckService.hasAccess(linkedDiscordId).whenComplete((hasAccess, error) ->
                    Server.getServer().execute(() -> {
                        ServerPlayerEntity currentPlayer = Server.getServer().getPlayerManager().getPlayer(playerUuidFinal);
                        if (currentPlayer == null || !PlayerAuthManager.isPendingAuth(currentPlayer)) {
                            return;
                        }

                        if (error != null) {
                            PlayerAuthManager.kickPlayerWithMessage(currentPlayer, "Internal error");
                            return;
                        }

                        if (!hasAccess.allowed()) {
                            String kickMessage = handleDeny(currentPlayer, hasAccess.type());
                            PlayerAuthManager.kickPlayerWithMessage(player, kickMessage);
                            return;
                        }

                        completeAuth(currentPlayer, resolvedPlayerId, true);
                    })
            );
        });
    }

    public static String handleDeny(ServerPlayerEntity player, RoleCheckService.AccessResultType type) {
        String message = switch (type) {
            case ERROR -> "Error checking Discord roles";
            case NO_GUILD -> "Cannot access Discord server";
            case MISCONFIG -> "Server misconfigured for Discord authentication";
            case ROLE_MISSING -> ConfigManager.msg().auth.missingRequiredDiscordRole;
            case NOT_A_MEMBER -> ConfigManager.msg().auth.missingRequiredDiscordGuild;
            case TIMEOUT -> "Discord role check timed out";
            default -> "Access denied";
        };
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
        return message;
    }

    public static void onDiscordApprove(ServerPlayerEntity player, Object playerId, String discordUserId, Consumer<DiscordApprovalResult> callback) {
        Server.getServer().execute(() -> {
            if (!PlayerAuthManager.isPendingAuth(player)) {
                callback.accept(DiscordApprovalResult.NOT_PENDING);
                return;
            }

            UUID playerUuid = player.getUuid();
            RoleCheckService.hasAccess(discordUserId).whenComplete((hasAccess, error) ->
                    Server.getServer().execute(() -> {
                        ServerPlayerEntity currentPlayer = Server.getServer().getPlayerManager().getPlayer(playerUuid);
                        if (currentPlayer == null || !PlayerAuthManager.isPendingAuth(currentPlayer)) {
                            callback.accept(DiscordApprovalResult.NOT_PENDING);
                            return;
                        }

                        if (error != null) {
                            PlayerAuthManager.kickPlayerWithMessage(currentPlayer, "Internal error");
                            return;
                        }

                        if (!hasAccess.allowed()) {
                            String kickMessage = handleDeny(currentPlayer, hasAccess.type());
                            PlayerAuthManager.kickPlayerWithMessage(player, kickMessage);
                            callback.accept(DiscordApprovalResult.ROLE_DENIED);
                            return;
                        }

                        completeAuth(currentPlayer, playerId, true);
                        callback.accept(DiscordApprovalResult.APPROVED);
                    })
            );
        });
    }

    public static void onDiscordReject(ServerPlayerEntity player) {
        Server.getServer().execute(() -> PlayerAuthManager.kickPlayerRejected(player));
    }

    private static void startNotifyFlow(ServerPlayerEntity player, Object playerId) {
        AuthStateManager.setStage(player.getUuid(), AuthStateManager.AuthStage.WAITING_DISCORD_LINK);

        String loginMessage = ConfigManager.msg().auth.loginRequired + "\n";
        player.sendMessage(Text.literal(loginMessage).formatted(Formatting.YELLOW), false);
        if (resolveAuthFlowMode() == AuthFlowMode.OAUTH_NOTIFY_FALLBACK) {
            player.sendMessage(Text.literal(ConfigManager.msg().auth.oauthFallbackToDiscord).formatted(Formatting.GRAY), false);
        }

        DatabaseManager db = Server.getDatabase();
        String linkedDiscordId = null;
        if (db != null && playerId != null) {
            linkedDiscordId = db.getPlayerDiscordId(playerId);
        }

        if (linkedDiscordId == null || linkedDiscordId.isBlank()) {
            String nonce = DiscordBotManager.createLinkNonce(player.getUuid(), player.getName().getString());
            String command = "/link " + nonce;
            Text copyableCommand = Text.literal(command)
                    .styled(style -> style
                            .withColor(Formatting.AQUA)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy command"))));
            player.sendMessage(Text.literal("Registration required. Use ")
                    .append(copyableCommand)
                    .append(Text.literal(" in Discord to link your account."))
                    .formatted(Formatting.GOLD), false);
        } else if (Server.getDsBot() != null) {
            Server.getDsBot().sendConfirm(playerId, player.getIp());
        }

        PlayerAuthManager.applyLoginRestrictions(player);
    }

    private static void startOAuthProbe(ServerPlayerEntity player, Runnable onTimeout) {
        PlayerAuthManager.applyLoginRestrictions(player);
        AuthStateManager.startProbe(player, PROBE_TIMEOUT_SECONDS, onTimeout);
        String nonce = UUID.randomUUID().toString();
        AuthStateManager.storePendingNonce(player.getUuid(), nonce);
        var probePacket = PacketByteBufs.create();
        probePacket.writeString(nonce);
        if (!ServerPlayNetworking.canSend(player, ModPackets.PROBE_ID)) {
            return;
        }
        ServerPlayNetworking.send(player, ModPackets.PROBE_ID, probePacket);
    }

    private static void onOAuthOnlyProbeTimeout(ServerPlayerEntity player) {
        player.sendMessage(Text.literal(ConfigManager.msg().auth.oauthOnlyClientRequired).formatted(Formatting.RED), false);
        PlayerAuthManager.kickPlayer(player);
    }

    public static boolean isOAuthEnabledForCurrentMode() {
        AuthFlowMode mode = resolveAuthFlowMode();
        return mode == AuthFlowMode.OAUTH_ONLY || mode == AuthFlowMode.OAUTH_NOTIFY_FALLBACK;
    }

    private static AuthFlowMode resolveAuthFlowMode() {
        String mode = ConfigManager.conf().discord.authFlowMode;
        if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("legacy")) {
            return ConfigManager.conf().discord.useDiscordOAuth ? AuthFlowMode.OAUTH_NOTIFY_FALLBACK : AuthFlowMode.NOTIFY_ONLY;
        }

        return switch (mode.toLowerCase()) {
            case "oauth_only" -> AuthFlowMode.OAUTH_ONLY;
            case "notify_only" -> AuthFlowMode.NOTIFY_ONLY;
            case "oauth_notify_fallback" -> AuthFlowMode.OAUTH_NOTIFY_FALLBACK;
            default -> ConfigManager.conf().discord.useDiscordOAuth ? AuthFlowMode.OAUTH_NOTIFY_FALLBACK : AuthFlowMode.NOTIFY_ONLY;
        };
    }

    private static boolean isAutoLoginAllowed(DatabaseManager db, Object playerId, String playerIp) {
        Long lastLoginTime = db.getPlayerLastLoginTime(playerId);
        if (lastLoginTime == null) {
            return false;
        }

        if (!playerIp.equals(db.getPlayerIp(playerId))) {
            return false;
        }

        long diffTS = System.currentTimeMillis() - lastLoginTime;
        return diffTS <= ConfigManager.conf().login.autoLoginTime * MILLIS_PER_HOUR;
    }

    private static void completeAuth(ServerPlayerEntity player, Object playerId, boolean updateLoginMeta) {
        Server.getServer().execute(() -> {
            // Idempotency guard: only the winning completion call may perform side effects.
            if (!AuthStateManager.completeAuth(player.getUuid())) return;
            DatabaseManager db = Server.getDatabase();
            if (db == null) {
                player.sendMessage(Text.literal("Authentication service unavailable").formatted(Formatting.RED), false);
                PlayerAuthManager.kickPlayer(player);
                return;
            }
            if (updateLoginMeta) {
                db.setPlayerIp(playerId, player.getIp());
                db.setPlayerLastLoginTime(playerId, System.currentTimeMillis());
            }
            if (ServerPlayNetworking.canSend(player, ModPackets.REFRESH_TOKEN_SYNC_ID)) {
                String token = RefreshTokenService.issueAndStore(player.getName().getString());
                var packet = PacketByteBufs.create();
                packet.writeString(token);
                ServerPlayNetworking.send(player, ModPackets.REFRESH_TOKEN_SYNC_ID, packet);
            }
            PlayerAuthManager.unblockPlayer(player);
        });
    }

    private static boolean tryInstantRefreshAuth(ServerPlayerEntity player, Object playerId, String refreshToken, String clientBindingHash, String clientMachineIdHash, String clientServerAddress, Runnable onRoleDenied) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        if (!RefreshTokenService.validateAndRevokeOnReuse(
                player.getName().getString(),
                player.getUuid(),
                refreshToken,
                clientBindingHash,
                clientMachineIdHash,
                clientServerAddress)) {
            return false;
        }

        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            player.sendMessage(Text.literal("Authentication service unavailable").formatted(Formatting.RED), false);
            PlayerAuthManager.kickPlayer(player);
            return false;
        }

        String discordId = db.getPlayerDiscordId(playerId);
        if (discordId == null || discordId.isBlank()) {
            return false;
        }

        UUID playerUuid = player.getUuid();
        RoleCheckService.hasAccess(discordId).whenComplete((hasAccess, error) ->
                Server.getServer().execute(() -> {
                    ServerPlayerEntity currentPlayer = Server.getServer().getPlayerManager().getPlayer(playerUuid);
                    if (currentPlayer == null || !PlayerAuthManager.isPendingAuth(currentPlayer)) {
                        return;
                    }

                    if (error != null) {
                        PlayerAuthManager.kickPlayerWithMessage(currentPlayer, "Internal error");
                        return;
                    }

                    if (!hasAccess.allowed()) {
                        handleDeny(currentPlayer, hasAccess.type());
                        onRoleDenied.run();
                        return;
                    }

                    completeAuth(currentPlayer, playerId, true);
                })
        );
        return true;
    }

    private static void beginOAuthFlow(ServerPlayerEntity player) {
        String state = AuthStateManager.generateState(player.getUuid());
        String authUrl = DiscordOauthService.buildAuthUrl(state);
        Text oauthLink = Text.literal("[Discord OAuth]")
                .styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withFormatting(Formatting.UNDERLINE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, authUrl)));

        var uiPacket = PacketByteBufs.create();
        uiPacket.writeString(authUrl);
        if (!ServerPlayNetworking.canSend(player, ModPackets.OAUTH_UI_ID)) {
            kickRegistrationUnavailable(player);
            return;
        }
        ServerPlayNetworking.send(player, ModPackets.OAUTH_UI_ID, uiPacket);

        player.sendMessage(Text.literal("Complete authorization in browser: ").append(oauthLink), false);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void kickRegistrationUnavailable(ServerPlayerEntity player) {
        AuthStateManager.cleanup(player.getUuid());
        player.networkHandler.disconnect(Text.literal("Registration unavailable, try again later"));
    }
}



