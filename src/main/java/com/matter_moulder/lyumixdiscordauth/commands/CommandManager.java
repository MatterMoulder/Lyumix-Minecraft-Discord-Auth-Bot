package com.matter_moulder.lyumixdiscordauth.commands;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class CommandManager {
    private static final DatabaseManager db = Main.getDatabase();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("lda")
            .requires(source -> source.hasPermissionLevel(4)) // Only for operators
            .then(literal("reload")
                .executes(context -> {
                    try {
                        ConfigMngr.load();
                        context.getSource().sendMessage(Text.literal(ConfigMngr.msg().admin.configReloaded));
                        return 1;
                    } catch (Exception e) {
                        context.getSource().sendMessage(Text.literal(ConfigMngr.msg().admin.configReloadFailed));
                        Main.getPluginLogger().error("Failed to reload configuration:", e);
                        return 0;
                    }
                })
            )
            .then(literal("force")
                .then(literal("unlink")
                    .then(literal("player")
                        .then(argument("name", StringArgumentType.word())
                            .executes(context -> {
                                String targetPlayerName = StringArgumentType.getString(context, "name");
                                Object playerId = db.getPlayerIdByName(targetPlayerName);
                                
                                if (playerId == null) {
                                    context.getSource().sendMessage(Text.literal(String.format(
                                        ConfigMngr.msg().admin.noLinkedAccountPlayer, targetPlayerName)));
                                    return 0;
                                }

                                db.deletePlayerData(playerId);
                                ServerPlayerEntity targetPlayer = Main.getPlayerByName(targetPlayerName);
                                if (targetPlayer != null) {
                                    DenyHandle.kickPlayer(targetPlayer);
                                }
                                
                                context.getSource().sendMessage(Text.literal(
                                    ConfigMngr.msg().discord.accountUnlinked.formatted(targetPlayerName)));
                                return 1;
                            })
                        )
                    )
                    .then(literal("discord")
                        .then(argument("id", StringArgumentType.string())
                            .executes(context -> {
                                String discordId = StringArgumentType.getString(context, "id");
                                Object playerId = db.getPlayerIdByDiscordId(discordId);
                                
                                if (playerId == null) {
                                    context.getSource().sendMessage(Text.literal(String.format(
                                        ConfigMngr.msg().admin.noLinkedAccountDiscord, discordId)));
                                    return 0;
                                }

                                String playerName = db.getPlayerName(playerId);
                                db.deletePlayerData(playerId);
                                
                                ServerPlayerEntity targetPlayer = Main.getPlayerByName(playerName);
                                if (targetPlayer != null) {
                                    DenyHandle.kickPlayer(targetPlayer);
                                }
                                
                                context.getSource().sendMessage(Text.literal(
                                    ConfigMngr.msg().discord.accountUnlinked.formatted(playerName)));
                                return 1;
                            })
                        )
                    )
                )
                .then(literal("link")
                    .then(argument("player", StringArgumentType.word())
                    .then(argument("discordId", StringArgumentType.string())
                        .executes(context -> {
                            String targetPlayerName = StringArgumentType.getString(context, "player");
                            String discordId = StringArgumentType.getString(context, "discordId");
                            
                            Object existingPlayerId = db.getPlayerIdByDiscordId(discordId);
                            if (existingPlayerId != null) {
                                context.getSource().sendMessage(Text.literal(
                                    ConfigMngr.msg().admin.discordAlreadyLinked));
                                return 0;
                            }

                            Object playerId = db.getPlayerIdByName(targetPlayerName);
                            if (playerId != null) {
                                context.getSource().sendMessage(Text.literal(
                                    ConfigMngr.msg().auth.alreadyRegistered));
                                return 0;
                            }

                            try {
                                db.savePlayerData(targetPlayerName, "0.0.0.0", discordId);
                            } catch (Exception e) {
                                context.getSource().sendMessage(Text.literal(String.format(
                                    ConfigMngr.msg().admin.forceLinkFailed, targetPlayerName)));
                                return 0;
                            }

                            ServerPlayerEntity targetPlayer = Main.getPlayerByName(targetPlayerName);
                            if (targetPlayer != null) {
                                DenyHandle.unblockPlayer(targetPlayer);
                            }

                            context.getSource().sendMessage(Text.literal(String.format(
                                ConfigMngr.msg().admin.forceLinkSuccess, targetPlayerName)));
                            return 1;
                        })
                    ))
                )
            )
        );
    }
} 