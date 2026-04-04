package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.SessionMngr;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.net.SocketAddress;
import java.nio.file.Path;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    private static final long MILLIS_PER_HOUR = 60 * 60 * 1000;

    @Unique
    private final PlayerManager playerManager = (PlayerManager) (Object) this;

    @Final
    @Shadow
    private MinecraftServer server;

    @Inject(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        String playersIp = player.getIp();
        DatabaseManager db = Main.getDatabase();

        Object playerId = db.getPlayerIdByName(player.getName().getString());

        if (playerId != null) {
            Long lastLoginTime = db.getPlayerLastLoginTime(playerId);

            if (db.getPlayerIp(playerId).equals(playersIp) && lastLoginTime != null) {
                Long currentTime = System.currentTimeMillis();
                long diffTS = currentTime - lastLoginTime;
                if (diffTS <= ConfigMngr.conf().login.autoLoginTime * MILLIS_PER_HOUR) {
                    DenyHandle.unblockPlayer(player);
                    return;
                }
            }
            String loginMessage = ConfigMngr.msg().auth.loginRequired + "\n";
            Text message = Text.literal(loginMessage).formatted(Formatting.YELLOW);

            player.sendMessage(message, false);
            Main.getDsBot().sendConfirm(playerId, player.getIp());

            if (ConfigMngr.conf().login.blindnessWhileLogin) {
                StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.BLINDNESS, Integer.MAX_VALUE, 255);
                player.addStatusEffect(effect);
            }

            Timer.startLoginTimer(player);
        }
    }

    @ModifyVariable(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("STORE"), ordinal = 0)
    private RegistryKey<World> onPlayerConnect(RegistryKey<World> world, ClientConnection connection,
            ServerPlayerEntity player) {
        PlayerRestoredInfo cache;
        if (!SessionMngr.contains(player.getUuid())) {
            cache = PlayerRestoredInfo.fromPlayer(player);
            SessionMngr.put(player.getUuid(), cache);
        }

        ((PlayerAuth) player).lda$saveLastDimension(world);
        return RegistryKey.of(RegistryKeys.WORLD, Main.getServer().getOverworld().getRegistryKey().getValue());
    }

    @ModifyArgs(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestTeleport(DDDFF)V"))
    private void onPlayerConnect(Args args, ClientConnection connection, ServerPlayerEntity player) {
        PlayerRestoredInfo cache = SessionMngr.get(((PlayerAuth) player).lda$getUuid());
        ((PlayerAuth) player).lda$saveLastLocation();

        NbtCompound nbtCompound = playerManager.loadPlayerData(player);
        if (nbtCompound != null && nbtCompound.contains("RootVehicle", 10)) {
            NbtCompound nbtCompound2 = nbtCompound.getCompound("RootVehicle");
            if (nbtCompound2.containsUuid("Attach")) {
                cache.ridingEntityUUID = nbtCompound2.getUuid("Attach");
            } else {
                cache.ridingEntityUUID = null;
            }
        }
        BlockPos spawnPos = Main.getServer().getOverworld().getSpawnPos();
        args.set(0, (double) spawnPos.getX());
        args.set(1, (double) spawnPos.getY());
        args.set(2, (double) spawnPos.getZ());

        Path playerDataPath = Main.getSessionDir().resolve(player.getUuid().toString() + ".json");
        if (playerDataPath.toFile().exists()) {
            cache.load();
        }
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void onRespawnPlayer(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity respawned = cir.getReturnValue();

        if (!alive && DenyHandle.checkPlayer(player)) {
            BlockPos spawnPos = Main.getServer().getOverworld().getSpawnPos();
            respawned.teleport(
                    Main.getServer().getOverworld(),
                    spawnPos.getX(),
                    spawnPos.getY(),
                    spawnPos.getZ(),
                    0, 0
            );
        }
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;startRiding(Lnet/minecraft/entity/Entity;Z)Z"))
    private boolean onPlayerConnectStartRiding(ServerPlayerEntity instance, Entity entity, boolean force,
            ClientConnection connection, ServerPlayerEntity player) {
        if (DenyHandle.checkPlayer(player)) {
            return false;
        }
        return instance.startRiding(entity, force);
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasVehicle()Z"))
    private boolean onPlayerConnectStartRiding(ServerPlayerEntity instance, ClientConnection connection,
            ServerPlayerEntity player) {
        if (DenyHandle.checkPlayer(player)) {
            return true;
        }
        return instance.hasVehicle();
    }

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayerEntity serverPlayerEntity, CallbackInfo ci) {
        DenyHandle.onPlayerLeave(serverPlayerEntity);
    }

    /**
     * Filters join/leave messages for unauthenticated players
     */
    @Inject(at = @At("HEAD"), method = "broadcast(Lnet/minecraft/text/Text;Z)V", cancellable = true)
    void filterBroadCastMessages(Text message, boolean overlay, CallbackInfo ci) {
        String messageString = message.getString();
        String playerName = null;
        
        if (messageString.contains("joined the game")) {
            playerName = messageString.replace(" joined the game", "");
        } else if (messageString.contains("left the game")) {
            playerName = messageString.replace(" left the game", "");
        }

        ServerPlayerEntity player = playerManager.getPlayer(playerName);

        if (player == null) return;

        if (playerName != null && (DenyHandle.checkPlayer(player.getUuid()))) {
            ci.cancel();
        }
    }

    @Inject(at = @At("RETURN"), method = "checkCanJoin", cancellable = true)
	private void init(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        Object playerId = Main.getDatabase().getPlayerIdByName(profile.getName());
        if (playerId == null) {
            cir.setReturnValue(Text.literal(ConfigMngr.msg().auth.notRegistered));
            return;
        }
    }
}

