package com.matter_moulder.lyumixdiscordauth.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "canTakeItems(Lnet/minecraft/entity/player/PlayerEntity;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void canTakeItems(PlayerEntity playerEntity, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;
        ActionResult result = DenyHandle.onTakeItem(player);
        if (result == ActionResult.FAIL) {
            player.networkHandler.sendPacket(
                    new ScreenHandlerSlotUpdateS2CPacket(
                            -2,
                            0,
                            player.getInventory().selectedSlot,
                            player.getInventory().getStack(player.getInventory().selectedSlot))
            );
            cir.setReturnValue(false);
        }
    }
}
