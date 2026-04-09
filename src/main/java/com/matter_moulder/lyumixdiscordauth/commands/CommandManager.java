package com.matter_moulder.lyumixdiscordauth.commands;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.config.SecretService;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.db.SQLiteManager;
import com.matter_moulder.lyumixdiscordauth.discord.DiscordBotManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class CommandManager {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("link")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }

                    String nonce = DiscordBotManager.createLinkNonce(player.getUuid(), player.getName().getString());
                    String command = "/link " + nonce;
                    Text copyableCommand = Text.literal(command)
                            .styled(style -> style
                                    .withColor(Formatting.AQUA)
                                    .withUnderline(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy command"))));
                    context.getSource().sendMessage(Text.literal("Use ")
                            .append(copyableCommand)
                            .append(Text.literal(" in Discord within 5 minutes to link your account.")));
                    return 1;
                })
        );

        dispatcher.register(literal("lda")
            .requires(source -> source.hasPermissionLevel(4)) // Only for operators
            .then(literal("reload")
                .executes(context -> {
                    try {
                        ConfigManager.load();
                        SecretService.syncSecretsFromConfig();
                        context.getSource().sendMessage(Text.literal(ConfigManager.msg().admin.configReloaded));
                        return 1;
                    } catch (Exception e) {
                        context.getSource().sendMessage(Text.literal(ConfigManager.msg().admin.configReloadFailed));
                        Server.getPluginLogger().error("Failed to reload configuration:", e);
                        return 0;
                    }
                })
            )
            .then(literal("force")
                .then(literal("unlink")
                    .then(literal("player")
                        .then(argument("name", StringArgumentType.word())
                            .executes(context -> {
                                DatabaseManager db = requireDatabase(context.getSource());
                                if (db == null) {
                                    return 0;
                                }
                                String targetPlayerName = StringArgumentType.getString(context, "name");
                                Object playerId = db.getPlayerIdByName(targetPlayerName);
                                
                                if (playerId == null) {
                                    context.getSource().sendMessage(Text.literal(String.format(
                                        ConfigManager.msg().admin.noLinkedAccountPlayer, targetPlayerName)));
                                    return 0;
                                }

                                db.deletePlayerData(playerId);
                                ServerPlayerEntity targetPlayer = Server.getPlayerByName(targetPlayerName);
                                if (targetPlayer != null) {
                                    PlayerAuthManager.kickPlayer(targetPlayer);
                                }
                                
                                context.getSource().sendMessage(Text.literal(
                                    ConfigManager.msg().discord.accountUnlinked.formatted(targetPlayerName)));
                                return 1;
                            })
                        )
                    )
                    .then(literal("discord")
                        .then(argument("id", StringArgumentType.string())
                            .executes(context -> {
                                DatabaseManager db = requireDatabase(context.getSource());
                                if (db == null) {
                                    return 0;
                                }
                                String discordId = StringArgumentType.getString(context, "id");
                                Object playerId = db.getPlayerIdByDiscordId(discordId);
                                
                                if (playerId == null) {
                                    context.getSource().sendMessage(Text.literal(String.format(
                                        ConfigManager.msg().admin.noLinkedAccountDiscord, discordId)));
                                    return 0;
                                }

                                String playerName = db.getPlayerName(playerId);
                                db.deletePlayerData(playerId);
                                
                                ServerPlayerEntity targetPlayer = Server.getPlayerByName(playerName);
                                if (targetPlayer != null) {
                                    PlayerAuthManager.kickPlayer(targetPlayer);
                                }
                                
                                context.getSource().sendMessage(Text.literal(
                                    ConfigManager.msg().discord.accountUnlinked.formatted(playerName)));
                                return 1;
                            })
                        )
                    )
                )
                .then(literal("link")
                    .then(argument("player", StringArgumentType.word())
                    .then(argument("discordId", StringArgumentType.string())
                        .executes(context -> {
                            DatabaseManager db = requireDatabase(context.getSource());
                            if (db == null) {
                                return 0;
                            }
                            String targetPlayerName = StringArgumentType.getString(context, "player");
                            String discordId = StringArgumentType.getString(context, "discordId");
                            
                            Object playerId = db.getPlayerIdByName(targetPlayerName);
                            if (playerId != null) {
                                context.getSource().sendMessage(Text.literal(
                                    ConfigManager.msg().auth.alreadyRegistered));
                                return 0;
                            }

                            try {
                                db.savePlayerData(targetPlayerName, "0.0.0.0", discordId);
                            } catch (SQLiteManager.DiscordAlreadyLinkedException e) {
                                context.getSource().sendMessage(Text.literal(
                                        ConfigManager.msg().admin.discordAlreadyLinked));
                                return 0;
                            } catch (Exception e) {
                                context.getSource().sendMessage(Text.literal(String.format(
                                    ConfigManager.msg().admin.forceLinkFailed, targetPlayerName)));
                                return 0;
                            }

                            ServerPlayerEntity targetPlayer = Server.getPlayerByName(targetPlayerName);
                            if (targetPlayer != null) {
                                PlayerAuthManager.unblockPlayer(targetPlayer);
                            }

                            context.getSource().sendMessage(Text.literal(String.format(
                                ConfigManager.msg().admin.forceLinkSuccess, targetPlayerName)));
                            return 1;
                        })
                    ))
                )
            )
        );
    }

    private static DatabaseManager requireDatabase(ServerCommandSource source) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            source.sendMessage(Text.literal("Authentication service unavailable"));
        }
        return db;
    }
}