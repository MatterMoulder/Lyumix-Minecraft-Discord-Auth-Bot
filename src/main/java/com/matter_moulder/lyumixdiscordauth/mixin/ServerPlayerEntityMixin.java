package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.auth.SessionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
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

import com.matter_moulder.lyumixdiscordauth.models.Location;
import com.matter_moulder.lyumixdiscordauth.models.PlayerAuth;
import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;

import java.util.UUID;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements PlayerAuth {
    @Unique
    private final ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
    @Final
    @Shadow
    public MinecraftServer server;

    @Override
    public void lda$saveLastLocation() {
        PlayerRestoredInfo cache = SessionManager.get(this.lda$getUuid());

        cache.location = Location.fromPlayer(player, cache.location.getDimensionKey());
        cache.ridingEntityUUID = player.getVehicle() != null ? player.getVehicle().getUuid() : null;
        cache.wasDead = player.isDead();

        cache.save();
    }

    @Override
    public void lda$saveLastDimension(RegistryKey<World> registryKey) {
        PlayerRestoredInfo playerRestoredInfo = SessionManager.get(this.lda$getUuid());
        playerRestoredInfo.location = new Location(registryKey, 0, 0, 0, 0, 0);
    }

    @Override
    public void lda$restoreLastLocation() {
        PlayerRestoredInfo playerRestoredInfo = SessionManager.get(this.lda$getUuid());
        if (playerRestoredInfo.wasDead) {
            player.kill();
            player.getScoreboard().forEachScore(ScoreboardCriterion.DEATH_COUNT, player.getEntityName(), (score) -> score.setScore(score.getScore() - 1));
            return;
        }
        player.teleport(
                playerRestoredInfo.location.getDimensionKey() == null ? server.getWorld(World.OVERWORLD) : playerRestoredInfo.location.getWorld(),
                playerRestoredInfo.location.getX(),
                playerRestoredInfo.location.getY(),
                playerRestoredInfo.location.getZ(),
                playerRestoredInfo.location.getYaw(),
                playerRestoredInfo.location.getPitch());

        if (playerRestoredInfo.ridingEntityUUID != null) {

            if (playerRestoredInfo.location.getDimensionKey() == null) return;
            ServerWorld world = playerRestoredInfo.location.getWorld();
            if (world == null) return;
            Entity entity = world.getEntity(playerRestoredInfo.ridingEntityUUID);
            if (entity != null) {
                player.startRiding(entity, true);
            }
        }
    }

    @Override
    public UUID lda$getUuid() {
        return player.getUuid();
    }

    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void dropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> cir) {
        ActionResult result = PlayerAuthManager.onAnyAction(player);

        if (result == ActionResult.FAIL) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void blockDamageWhileAuth(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerAuthManager.isPendingAuth(player)) {
            cir.setReturnValue(false);
        }
    }

}