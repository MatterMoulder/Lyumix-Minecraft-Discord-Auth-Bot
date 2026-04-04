package com.matter_moulder.lyumixdiscordauth.handlers;

import java.util.UUID;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.SessionMngr;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

/**
 * Handles player restrictions and authentication state.
 * Controls player actions while they are not authenticated.
 */
public class DenyHandle {
    public static long lastAcceptedPacket = 0;

    public static ActionResult onPlayerMove(ServerPlayerEntity player) {
        if (checkPlayer(player)) {
            if (System.nanoTime() >= lastAcceptedPacket + 5 * 1000000) {
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                lastAcceptedPacket = System.nanoTime();
            }
            if (!player.isInvulnerable())
                player.setInvulnerable(true);
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        if (checkPlayer(player)) {
            ((PlayerAuth) player).lda$restoreLastLocation();
            if (player.interactionManager.getGameMode().isSurvivalLike()) {
                player.setInvulnerable(false);
                player.setInvisible(false);
            }
            SessionMngr.remove(player.getUuid());
        }
    }

    public static boolean onAnyBoolAction(PlayerEntity player) {
        return !checkPlayer(player.getUuid());
    }

    public static TypedActionResult<ItemStack> onUseItem(PlayerEntity player) {
        if (checkPlayer(player.getUuid())) {
            return TypedActionResult.fail(ItemStack.EMPTY);
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    public static ActionResult onAnyAction(PlayerEntity player) {
        if (checkPlayer(player.getUuid())) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    /**
     * Removes restrictions from a player after successful authentication
     * @param player The player to unblock
     */
    public static void unblockPlayer(ServerPlayerEntity player) {
        if (ConfigMngr.conf().login.blindnessWhileLogin) {
            Main.getServer().execute(() -> player.removeStatusEffect(StatusEffects.BLINDNESS));
        }
        if (player.interactionManager.getGameMode().isSurvivalLike()) {
            player.setInvulnerable(false);
        }
        Timer.stopLoginTimer(player);
        ((PlayerAuth) player).lda$restoreLastLocation();
        SessionMngr.remove(player.getUuid());
        
        Main.getServer().getPlayerManager().broadcast(Text.literal(String.format(ConfigMngr.msg().auth.joinMessage, player.getName().getString())).formatted(Formatting.YELLOW), false);
    }

    public static boolean checkPlayer(ServerPlayerEntity player) {
        return SessionMngr.contains(player.getUuid());
    }

    public static boolean checkPlayer(UUID uuid) {
        return SessionMngr.contains(uuid);
    }

    public static void kickPlayer(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.authenticationFailed));
    }

    public static void kickPlayerTimedOut(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.authenticationTimeout));
    }

    public static void kickPlayerRejected(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.loginRequestRejected));
    }
}
