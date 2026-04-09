package com.matter_moulder.lyumixdiscordauth.auth;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerAuthManager {
    private static final ConcurrentHashMap<UUID, Long> lastAcceptedPacketByPlayer = new ConcurrentHashMap<>();

    public static void applyLoginRestrictions(ServerPlayerEntity player) {
        if (ConfigManager.conf().login.blindnessWhileLogin) {
            StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.BLINDNESS, Integer.MAX_VALUE, 255);
            player.addStatusEffect(effect);
        }

        Timer.startLoginTimer(player);
    }

    public static void unblockPlayer(ServerPlayerEntity player) {
        if (ConfigManager.conf().login.blindnessWhileLogin) {
            Server.getServer().execute(() -> player.removeStatusEffect(StatusEffects.BLINDNESS));
        }
        Timer.stopLoginTimer(player);
        ((PlayerAuth) player).lda$restoreLastLocation();
        SessionManager.remove(player.getUuid());

        Server.getServer().getPlayerManager().broadcast(Text.literal(String.format(ConfigManager.msg().auth.joinMessage, player.getName().getString())).formatted(Formatting.YELLOW), false);
    }

    public static boolean isPendingAuth(ServerPlayerEntity player) {
        return AuthStateManager.isPendingAuth(player.getUuid());
    }

    public static boolean isPendingAuth(UUID uuid) {
        return AuthStateManager.isPendingAuth(uuid);
    }

    public static boolean canAnyAction(PlayerEntity player) {
        return !isPendingAuth(player.getUuid());
    }

    public static TypedActionResult<ItemStack> onUseItem(PlayerEntity player) {
        if (isPendingAuth(player.getUuid())) {
            return TypedActionResult.fail(ItemStack.EMPTY);
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    public static ActionResult onAnyAction(PlayerEntity player) {
        if (isPendingAuth(player.getUuid())) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        lastAcceptedPacketByPlayer.remove(player.getUuid());
        if (isPendingAuth(player)) {
            ((PlayerAuth) player).lda$restoreLastLocation();
            SessionManager.remove(player.getUuid());
        }
        AuthStateManager.cleanup(player.getUuid());
    }

    public static ActionResult onPlayerMove(ServerPlayerEntity player) {
        if (isPendingAuth(player)) {
            UUID playerUuid = player.getUuid();
            long now = System.nanoTime();
            long lastAcceptedPacket = lastAcceptedPacketByPlayer.getOrDefault(playerUuid, 0L);
            if (now >= lastAcceptedPacket + 5 * 1000000) {
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                // Track move throttling per player so one user cannot throttle others.
                lastAcceptedPacketByPlayer.put(playerUuid, now);
            }
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static void kickPlayer(ServerPlayerEntity player) {
        disconnectWithCleanup(player, ConfigManager.msg().auth.authenticationFailed);
    }

    public static void kickPlayerTimedOut(ServerPlayerEntity player) {
        disconnectWithCleanup(player, ConfigManager.msg().auth.authenticationTimeout);
    }

    public static void kickPlayerRejected(ServerPlayerEntity player) {
        disconnectWithCleanup(player, ConfigManager.msg().auth.loginRequestRejected);
    }

    private static void disconnectWithCleanup(ServerPlayerEntity player, String message) {
        AuthStateManager.cleanup(player.getUuid());
        player.networkHandler.disconnect(Text.of(message));
    }

    public static void clearLastAcceptedPacket(UUID playerUuid) {
        lastAcceptedPacketByPlayer.remove(playerUuid);
    }
}
