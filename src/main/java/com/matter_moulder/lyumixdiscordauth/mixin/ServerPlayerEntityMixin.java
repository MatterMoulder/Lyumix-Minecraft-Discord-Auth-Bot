package com.matter_moulder.lyumixdiscordauth.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.models.Location;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;
import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements PlayerAuth {
    @Unique
    private final ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
    @Final
    @Shadow
    public MinecraftServer server;

    @Override
    public void lda$saveLastLocation(boolean saveDimension) {
        PlayerRestoredInfo cache = Main.playerCache.get(this.lda$getName());

        cache.location = Location.fromPlayer(player);
        cache.ridingEntityUUID = player.getVehicle() != null ? player.getVehicle().getUuid() : null;
        cache.wasDead = player.isDead();

        if (saveDimension) {
            cache.dimension = player.getServerWorld();
        }
    }

    @Override
    public void lda$saveLastDimension(RegistryKey<World> registryKey) {
        PlayerRestoredInfo playerRestoredInfo = Main.playerCache.get(this.lda$getName());
        playerRestoredInfo.dimension = this.server.getWorld(registryKey);
    }

    @Override
    public void lda$restoreLastLocation() {
        PlayerRestoredInfo playerRestoredInfo = Main.playerCache.get(this.lda$getName());
        if (playerRestoredInfo.wasDead) {
            player.kill();
            player.getScoreboard().forEachScore(ScoreboardCriterion.DEATH_COUNT, player, (score) -> score.setScore(score.getScore() - 1));
            return;
        }
        player.teleport(
                playerRestoredInfo.dimension == null ? server.getWorld(World.OVERWORLD) : playerRestoredInfo.dimension,
                playerRestoredInfo.location.getX(),
                playerRestoredInfo.location.getY(),
                playerRestoredInfo.location.getZ(),
                playerRestoredInfo.location.getYaw(),
                playerRestoredInfo.location.getPitch());

        if (playerRestoredInfo.ridingEntityUUID != null) {

            if (playerRestoredInfo.dimension == null) return;
            ServerWorld world = server.getWorld(playerRestoredInfo.dimension.getRegistryKey());
            if (world == null) return;
            Entity entity = world.getEntity(playerRestoredInfo.ridingEntityUUID);
            if (entity != null) {
                player.startRiding(entity, true);
            }
        }
    }

    @Override
    public String lda$getName() {
        return player.getName().getString();
    }

    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void dropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        ActionResult result = DenyHandle.onDropItem(player);

        if (result == ActionResult.FAIL) {
            cir.setReturnValue(false);
        }
    }
}