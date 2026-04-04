package com.matter_moulder.lyumixdiscordauth.discord;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

import com.matter_moulder.lyumixdiscordauth.Main;
import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import net.minecraft.util.UserCache;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Discord bot functionality including command handling and authentication.
 * Handles Discord-Minecraft account linking and login approval process.
 */
public class BotMngr extends ListenerAdapter {
    private final DatabaseManager db = Main.getDatabase();
    private static boolean tokenCorrupted = false;
    private static JDA builder;
    public static boolean isBotRunning = false;

    public BotMngr() {
        String token = ConfigMngr.conf().discord.botToken;
        try {
            builder = JDABuilder.createDefault(token)
                    .addEventListeners(this)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            Main.getPluginLogger().error("Bot failed to start, check token in config.hocon", e);
            tokenCorrupted = true;
            return;
        }
        isBotRunning = true;
        
        CommandListUpdateAction commands = builder.updateCommands();
        commands.addCommands(
                Commands.slash("register", "Link your Minecraft account")
                        .addOption(STRING, "username", "Your Minecraft username", true)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
                Commands.slash("instructions", "Get server connection instructions")
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
                Commands.slash("status", "Check server status")
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
                Commands.slash("unlink", "Unlink your Minecraft account from Discord")
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
                Commands.slash("link", "Force link someone Discord account to their Minecraft account (Admin only)")
                        .addOption(STRING, "username", "Minecraft username to link", true)
                        .addOption(USER, "discord_user", "Discord user to link", true)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                Commands.slash("forceunlink", "Force unlink someone Discord account from their Minecraft account (Admin only)")
                        .addOption(USER, "discord_user", "Discord user to unlink", true)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
//                Commands.slash("ban", "Ban a player from linking their account (Admin only)")
//                        .addOption(USER, "discord_user", "Discord user to ban", false)
//                        .addOption(STRING, "username", "Minecraft username to ban", false)
//                        .setGuildOnly(true)
//                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
//                Commands.slash("unban", "Unban a player from linking their account (Admin only)")
//                        .addOption(USER, "discord_user", "Discord user to unban", false)
//                        .addOption(STRING, "username", "Minecraft username to unban", false)
//                        .setGuildOnly(true)
//                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))

        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        
        if (!ConfigMngr.conf().discord.discordServerId.isEmpty()) {
            try {
                if (!event.getGuild().getId().equals(ConfigMngr.conf().discord.discordServerId)) {
                    event.reply(ConfigMngr.msg().discord.wrongServer).setEphemeral(true).queue();
                    return;
                }
            } catch (Exception e) {
                Main.getPluginLogger().error("Error checking server ID", e);
                event.reply("Error checking server ID").setEphemeral(true).queue();
                return;
            }
        }
        
        switch (event.getName()) {
            case "register" -> register(event, Objects.requireNonNull(event.getOption("username")).getAsString());
            case "instructions" -> serverInstruction(event);
            case "status" -> checkStatus(event);
            case "unlink" -> unlinkAccount(event);
            default -> event.reply("Unknown command").setEphemeral(true).queue();
        }
    }

    public void serverInstruction(SlashCommandInteractionEvent event) {
        event.reply(ConfigMngr.msg().discord.serverIpMessage).setEphemeral(true).queue();
    }

    /**
     * Handles the registration command from Discord.
     * Links a Discord account to a Minecraft account using the provided code.
     */
    public void register(SlashCommandInteractionEvent event, String username) {
        event.deferReply(true).queue();
        
        String discordId = event.getUser().getId();
        if (db.getPlayerIdByDiscordId(discordId) != null) {
            event.getHook().sendMessage(ConfigMngr.msg().auth.alreadyRegistered).queue();
            return;
        }

        db.savePlayerData(username, "0.0.0.0", discordId);

        if (Main.getServer().getPlayerManager().isWhitelistEnabled()) {
            whitelistUser(username);
        }

        event.getHook().sendMessage(ConfigMngr.msg().auth.registrationSuccess).queue();
    }

    private void whitelistUser(String username) {
        MinecraftServer server = Main.getServer();
        PlayerManager playerManager = server.getPlayerManager();
        Whitelist whitelist = playerManager.getWhitelist();
        UserCache userCache = server.getUserCache();


        Optional<GameProfile> optionalProfile = userCache != null ? userCache.findByName(username) : Optional.empty();

        GameProfile profile;

        if (optionalProfile.isPresent()) {
            profile = optionalProfile.get();
        } else {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            profile = new GameProfile(offlineUuid, username);
        }

        if (!whitelist.isAllowed(profile)) {
            WhitelistEntry entry = new WhitelistEntry(profile);
            whitelist.add(entry);
            try {
                whitelist.save();
            } catch (Exception e) {
                Main.getPluginLogger().error("Failed to save whitelist after adding player: {}", username, e);
                return;
            }

            System.out.println("[Whitelist] Игрок " + username + " добавлен в whitelist (UUID: " + profile.getId() + ")");
        } else {
            System.out.println("[Whitelist] Игрок " + username + " уже в whitelist");
        }
    }

    /**
     * Handles the unlink command from Discord.
     * Unlinks a Discord account from the Minecraft account if enabled in config.
     */
    public void unlinkAccount(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!ConfigMngr.conf().discord.allowUserUnlink) {
            event.getHook().sendMessage(ConfigMngr.msg().discord.unlinkNotAllowed).queue();
            return;
        }

        String discordId = event.getUser().getId();
        Object playerId = db.getPlayerIdByDiscordId(discordId);
        
        if (playerId == null) {
            event.getHook().sendMessage(ConfigMngr.msg().auth.notRegistered).setEphemeral(true).queue();
            return;
        }

        if (Main.getServer().getPlayerManager().getPlayer(db.getPlayerName(playerId)) != null) {
            event.getHook().sendMessage(ConfigMngr.msg().discord.unlinkWhileOnline).queue();
            return;
        }

        db.deletePlayerData(playerId);
        event.getHook().sendMessage(ConfigMngr.msg().discord.accountUnlinked.formatted(event.getUser().getName())).queue();

    }

    public void checkStatus(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String discordId = event.getUser().getId();
        Object playerId = db.getPlayerIdByDiscordId(discordId);
        if (playerId == null) {
            event.getHook().sendMessage(ConfigMngr.msg().auth.notRegistered).setEphemeral(true).queue();
            return;
        }

        event.getHook().sendMessage(ConfigMngr.msg().discord.accountLinkStatus.formatted(event.getUser().getName(), db.getPlayerName(playerId))).queue();
    }

    /**
     * Sends a confirmation message to a Discord user for login approval.

     * Includes player's IP and approval/rejection buttons.
     * 
     * @param playerId Database ID of the player requesting login
     */
    public void sendConfirm(Object playerId, String playerIp) {
        try {
            String discordId = db.getPlayerDiscordId(playerId);
            if (discordId == null) return;

            User user = builder.retrieveUserById(discordId).complete();
            if (user == null) return;

            String playerName = db.getPlayerName(playerId);

            user.openPrivateChannel().queue(channel -> channel.sendMessage(
                    String.format(
                        ConfigMngr.msg().discord.discordLoginRequest,
                        playerName,
                        playerIp
                    )
                )
//                        TODO: Add hash to ids
                .setActionRow(
                    Button.success("approve", ConfigMngr.msg().discord.loginApproved),
                    Button.danger("reject", ConfigMngr.msg().discord.loginRejected)
                ).queue());
        } catch (Exception e) {
            Main.getPluginLogger().error("Failed to send confirmation message", e);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ServerPlayerEntity player = null;
        String buttonId = event.getComponentId();
        Object playerId = db.getPlayerIdByDiscordId(event.getUser().getId());
        
        if (playerId != null) {
            try {
                player = Main.getPlayerByName(db.getPlayerName(playerId));
            } catch (Exception e) {
                Main.getPluginLogger().error("Error getting player by name", e);
                event.getMessage().delete().queue();
                return;
            }

            if (player != null && !player.isDisconnected()) {
                switch (buttonId) {
                    case "approve":
                        if (DenyHandle.checkPlayer(player)) {
                            unlockPlayer(player, playerId);
                            event.editMessage(ConfigMngr.msg().discord.loginApprovedMessage)
                                 .setComponents()
                                 .queue();
                            Main.getPluginLogger().info("Login approved for player: {}", player.getName().getString());
                        } else {
                            event.getMessage().delete().queue();
                        }
                        break;

                    case "reject":
                        if (DenyHandle.checkPlayer(player)) {
                            kickPlayer(player);
                            event.editMessage(ConfigMngr.msg().discord.loginRejectedMessage)
                                 .setComponents()
                                 .queue();
                            Main.getPluginLogger().info("Login rejected for player: {}", player.getName().getString());
                        } else {
                            event.getMessage().delete().queue();
                        }
                        break;

                    default:
                        Main.getPluginLogger().warn("Unknown button interaction: {}", buttonId);
                        event.reply(ConfigMngr.msg().discord.unknownButton).queue();
                }
            } else {
                Main.getPluginLogger().debug("Player not found or disconnected, removing message");
                event.getMessage().delete().queue();
            }
        }
    }

    private void unlockPlayer(ServerPlayerEntity player, Object playerId) {
        Main.getServer().execute(() -> {
            DenyHandle.unblockPlayer(player);
            updateIp(playerId);
            updateTimeStamp(playerId);
            Main.getPluginLogger().info("Player unlocked: {}", player.getName().getString());
        });
    }

    private void kickPlayer(ServerPlayerEntity player) {
        Main.getServer().execute(() -> {
            DenyHandle.kickPlayerRejected(player);
            Main.getPluginLogger().info("Player kicked: {}", player.getName().getString());
        });
    }

    private void updateTimeStamp(Object playerId) {
        Long currentTime = System.currentTimeMillis();
        db.setPlayerLastLoginTime(playerId, currentTime);
        Main.getPluginLogger().debug("Updated timestamp for player ID: {}", playerId);
    }

    private void updateIp(Object playerId) {
        String playerName = db.getPlayerName(playerId);
        ServerPlayerEntity player = Main.getPlayerByName(playerName);
        if (player != null) {
            String playerIp = player.getIp();
            db.setPlayerIp(playerId, playerIp);
            Main.getPluginLogger().debug("Updated IP for player: {} to: {}", playerName, playerIp);
        }
    }

    public boolean isTokenCorrupted() {
        return tokenCorrupted;
    }

    public void shutdown() {
        if (builder != null) {
            builder.shutdown();
        }
    }
}
