package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.net.SocketAddress;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;
import com.mojang.authlib.GameProfile;
import com.matter_moulder.lyumixdiscordauth.handlers.JoinHandle;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Unique
    private final PlayerManager playerManager = (PlayerManager) (Object) this;

    @Final
    @Shadow
    private MinecraftServer server;

    @Inject(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V", at = @At("RETURN"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData,
            CallbackInfo ci) {
        JoinHandle.onPlayerJoin(player);
    }

    @ModifyVariable(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V", at = @At("STORE"), ordinal = 0)
    private RegistryKey<World> onPlayerConnect(RegistryKey<World> world, ClientConnection connection,
            ServerPlayerEntity player, ConnectedClientData clientData) {
        PlayerRestoredInfo cache;
        String name = player.getName().getString();
        if (!Main.playerCache.containsKey(name)) {
            cache = PlayerRestoredInfo.fromJson(player, name);
            Main.playerCache.put(name, cache);
        }
        ((PlayerAuth) player).lda$saveLastDimension(world);
        return RegistryKey.of(RegistryKeys.WORLD, Main.getServer().getOverworld().getRegistryKey().getValue());
    }

    @ModifyArgs(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestTeleport(DDDFF)V"))
    private void onPlayerConnect(Args args, ClientConnection connection, ServerPlayerEntity player,
            ConnectedClientData clientData) {
        PlayerRestoredInfo cache = Main.playerCache.get(((PlayerAuth) player).lda$getName());
        ((PlayerAuth) player).lda$saveLastLocation(false);

        Optional<NbtCompound> nbtCompound = playerManager.loadPlayerData(player);
        if (nbtCompound.isPresent() && nbtCompound.get().contains("RootVehicle", 10)) {
            NbtCompound nbtCompound2 = nbtCompound.get().getCompound("RootVehicle");
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
    }

    @Redirect(method = "respawnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getRespawnTarget(ZLnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;"))
    private TeleportTarget replaceRespawnTarget(ServerPlayerEntity player, boolean alive,
            TeleportTarget.PostDimensionTransition postDimensionTransition) {
        if (!alive && DenyHandle.checkPlayer(player)) {
            BlockPos spawnPos = Main.getServer().getOverworld().getSpawnPos();
            return new TeleportTarget(
                    Main.getServer().getOverworld(),
                    new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()),
                    new Vec3d(0.0F, 0.0F, 0.0F), 0, 0, postDimensionTransition);
        }
        return player.getRespawnTarget(alive, postDimensionTransition);
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;startRiding(Lnet/minecraft/entity/Entity;Z)Z"))
    private boolean onPlayerConnectStartRiding(ServerPlayerEntity instance, Entity entity, boolean force,
            ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData) {
        if (DenyHandle.checkPlayer(player)) {
            return false;
        }
        return instance.startRiding(entity, force);
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasVehicle()Z"))
    private boolean onPlayerConnectStartRiding(ServerPlayerEntity instance, ClientConnection connection,
            ServerPlayerEntity player, ConnectedClientData clientData) {
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
        
        if (playerName != null && (DenyHandle.checkPlayer(playerName) || playerManager.getPlayer(playerName) == null)) {
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

