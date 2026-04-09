package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void blockPushAwayFromAuthPlayers(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (isAuthProtected(self) || isAuthProtected(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "collidesWith(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void blockCollidesWithAuthPlayers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (isAuthProtected(self) || isAuthProtected(entity)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean isAuthProtected(Entity entity) {
        return entity instanceof ServerPlayerEntity player && PlayerAuthManager.isPendingAuth(player);
    }
}

