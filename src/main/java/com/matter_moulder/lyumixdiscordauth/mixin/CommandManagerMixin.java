package com.matter_moulder.lyumixdiscordauth.mixin;

import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandManager.class)
public class CommandManagerMixin {
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void checkCanUseCommands(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        ServerPlayerEntity player = parseResults.getContext().getSource().getPlayer();
        if (DenyHandle.checkPlayer(player)) {
            ci.cancel();
        }
    }
}