package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.auth.AuthOrchestrator;
import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.auth.SessionManager;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

@Mixin(net.minecraft.server.PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Unique
    private final net.minecraft.server.PlayerManager playerManager = (net.minecraft.server.PlayerManager) (Object) this;

    @Final
    @Shadow
    private MinecraftServer server;

    @Inject(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        AuthOrchestrator.startAuth(player);
    }

    @ModifyVariable(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("STORE"), ordinal = 0)
    private RegistryKey<World> onPlayerConnect(RegistryKey<World> world, ClientConnection connection,
            ServerPlayerEntity player) {
        PlayerRestoredInfo cache;
        if (!SessionManager.contains(player.getUuid())) {
            cache = PlayerRestoredInfo.fromPlayer(player);
            SessionManager.put(player.getUuid(), cache);
        }

        ((PlayerAuth) player).lda$saveLastDimension(world);
        return RegistryKey.of(RegistryKeys.WORLD, Server.getServer().getOverworld().getRegistryKey().getValue());
    }

    @ModifyArgs(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestTeleport(DDDFF)V"))
    private void onPlayerConnect(Args args, ClientConnection connection, ServerPlayerEntity player) {
        PlayerRestoredInfo cache = SessionManager.get(((PlayerAuth) player).lda$getUuid());
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
        BlockPos spawnPos = Server.getServer().getOverworld().getSpawnPos();
        args.set(0, (double) spawnPos.getX());
        args.set(1, (double) spawnPos.getY());
        args.set(2, (double) spawnPos.getZ());

        Path playerDataPath = Server.getSessionDir().resolve(player.getUuid().toString() + ".json");
        if (playerDataPath.toFile().exists()) {
            cache.load();
        }
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void onRespawnPlayer(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity respawned = cir.getReturnValue();

        if (!alive && PlayerAuthManager.isPendingAuth(player)) {
            BlockPos spawnPos = Server.getServer().getOverworld().getSpawnPos();
            respawned.teleport(
                    Server.getServer().getOverworld(),
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
        if (PlayerAuthManager.isPendingAuth(player)) {
            return false;
        }
        return instance.startRiding(entity, force);
    }

    @Redirect(method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;hasVehicle()Z"))
    private boolean onPlayerConnectStartRiding(ServerPlayerEntity instance, ClientConnection connection,
            ServerPlayerEntity player) {
        if (PlayerAuthManager.isPendingAuth(player)) {
            return true;
        }
        return instance.hasVehicle();
    }

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayerEntity serverPlayerEntity, CallbackInfo ci) {
        PlayerAuthManager.onPlayerLeave(serverPlayerEntity);
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

        if (playerName != null && (PlayerAuthManager.isPendingAuth(player.getUuid()))) {
            ci.cancel();
        }
    }

    @Inject(at = @At("RETURN"), method = "checkCanJoin", cancellable = true)
	private void init(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            // Fail closed if auth backend is down instead of throwing during join checks.
            cir.setReturnValue(Text.literal("Authentication service unavailable"));
            return;
        }
    }
}

