package com.matter_moulder.lyumixdiscordauth.handlers;

import java.util.HashSet;
import java.util.Set;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;

import net.minecraft.entity.effect.StatusEffectInstance;
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

    /** Set of players currently blocked (not authenticated) */
    public static Set<ServerPlayerEntity> blockedPlayers = new HashSet<>();

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
        }
    }

    public static ActionResult onPlayerChat(ServerPlayerEntity player) {
        if (checkPlayer(player)) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static ActionResult onUseBlock(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static boolean onBreakBlock(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return false;
        }
        return true;
    }

    public static TypedActionResult<ItemStack> onUseItem(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return TypedActionResult.fail(ItemStack.EMPTY);
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    public static ActionResult onDropItem(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    public static ActionResult onTakeItem(ServerPlayerEntity player) {
        if (checkPlayer(player)) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    public static ActionResult onAttackEntity(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    public static ActionResult onUseEntity(PlayerEntity player) {
        if (checkPlayer(Main.getServer().getPlayerManager().getPlayer(player.getUuid()))) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    /**
     * Blocks a player and applies authentication restrictions
     * @param player The player to block
     */
    public static void blockPlayer(ServerPlayerEntity player) {
        if (ConfigMngr.conf().login.blindnessWhileLogin) {
            StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.BLINDNESS, Integer.MAX_VALUE, 255);
            player.addStatusEffect(effect);
        }
        Timer.startLoginTimer(player);
        blockedPlayers.add(player);
    }

    /**
     * Removes restrictions from a player after successful authentication
     * @param player The player to unblock
     */
    public static void unblockPlayer(ServerPlayerEntity player) {
        if (ConfigMngr.conf().login.blindnessWhileLogin) {
            Main.getServer().execute(() -> {
                    player.removeStatusEffect(StatusEffects.BLINDNESS);
            });
        }
        if (player.interactionManager.getGameMode().isSurvivalLike()) {
            player.setInvulnerable(false);
        }
        Timer.stopLoginTimer(player);
        ((PlayerAuth) player).lda$restoreLastLocation();
        blockedPlayers.remove(player);
        Main.getServer().getPlayerManager().broadcast(Text.literal(String.format(ConfigMngr.msg().auth.joinMessage, player.getName().getString())).formatted(Formatting.YELLOW), false);
    }

    public static boolean checkPlayer(ServerPlayerEntity player) {
        return blockedPlayers.contains(player);
    }

    public static boolean checkPlayer(String name) {
        ServerPlayerEntity player = Main.getServer().getPlayerManager().getPlayer(name);
        return player != null && blockedPlayers.contains(player);
    }

    public static void kickPlayer(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.authenticationFailed));
        blockedPlayers.remove(player);
    }

    public static void kickPlayerTimedOut(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.authenticationTimeout));
        blockedPlayers.remove(player);
    }

    public static void kickPlayerRejected(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.of(ConfigMngr.msg().auth.loginRequestRejected));
        blockedPlayers.remove(player);
    }
}
