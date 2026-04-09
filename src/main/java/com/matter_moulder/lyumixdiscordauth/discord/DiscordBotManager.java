package com.matter_moulder.lyumixdiscordauth.discord;

import com.matter_moulder.lyumixdiscordauth.auth.AuthOrchestrator;
import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.Utils;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
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

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.config.SecretService;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.db.SQLiteManager;
import net.minecraft.util.UserCache;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages Discord bot functionality including command handling and authentication.
 * Handles Discord-Minecraft account linking and login approval process.
 */
public class DiscordBotManager extends ListenerAdapter {
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";
    private static final long LINK_NONCE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static boolean tokenCorrupted = false;
    private static JDA builder;
    public static boolean isBotRunning = false;
    private final ScheduledExecutorService discordApiTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private static final ConcurrentHashMap<String, PendingButtonAction> pendingButtonActions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PendingLinkNonce> pendingLinkNonces = new ConcurrentHashMap<>();

    private record PendingButtonAction(Object playerId, String discordId, long expiresAt) {
    }

    private record PendingLinkNonce(UUID playerUuid, String playerName, long expiresAt) {
    }

    public DiscordBotManager() {
        String token = SecretService.getBotToken();
        try {
            builder = JDABuilder.createDefault(token)
                    .addEventListeners(this)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            Server.getPluginLogger().error("Bot failed to start, check token in config.hocon", e);
            tokenCorrupted = true;
            return;
        }
        isBotRunning = true;
        
        CommandListUpdateAction commands = builder.updateCommands();
        commands.addCommands(
                Commands.slash("link", "Link your Minecraft account with a one-time nonce from in-game /link")
                        .addOption(STRING, "nonce", "One-time link nonce from the game", true)
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
                Commands.slash("forcelink", "Force link someone Discord account to their Minecraft account (Admin only)")
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
        
        if (!ConfigManager.conf().discord.discordServerId.isEmpty()) {
            try {
                if (!event.getGuild().getId().equals(ConfigManager.conf().discord.discordServerId)) {
                    event.reply(ConfigManager.msg().discord.wrongServer).setEphemeral(true).queue();
                    return;
                }
            } catch (Exception e) {
                Server.getPluginLogger().error("Error checking server ID", e);
                event.reply("Error checking server ID").setEphemeral(true).queue();
                return;
            }
        }
        
        switch (event.getName()) {
            case "link" -> register(event, Objects.requireNonNull(event.getOption("nonce")).getAsString());
            case "instructions" -> serverInstruction(event);
            case "status" -> checkStatus(event);
            case "unlink" -> unlinkAccount(event);
            default -> event.reply("Unknown command").setEphemeral(true).queue();
        }
    }

    public void serverInstruction(SlashCommandInteractionEvent event) {
        event.reply(ConfigManager.msg().discord.serverIpMessage).setEphemeral(true).queue();
    }

    // Link is nonce-based only; usernames are never accepted as identity proof.
    public void register(SlashCommandInteractionEvent event, String nonce) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            event.reply("Authentication service unavailable.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        cleanupExpiredLinkNonces();
        PendingLinkNonce pending = pendingLinkNonces.remove(nonce == null ? "" : nonce.trim());
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            event.getHook().sendMessage(ConfigManager.msg().discord.invalidOrExpiredRequest).queue();
            return;
        }

        if (Server.getServer() == null) {
            event.getHook().sendMessage("Authentication service unavailable.").queue();
            return;
        }

        Object playerId = db.getPlayerIdByName(pending.playerName());
        String linkedDiscordId = playerId != null ? db.getPlayerDiscordId(playerId) : null;

        withPlayerOnlineCheck(pending.playerUuid(), online -> {
            if (!online) {
                event.getHook().sendMessage(ConfigManager.msg().discord.invalidOrExpiredRequest).queue();
                return;
            }

            String discordId = event.getUser().getId();
            try {
                if (playerId == null) {
                    db.savePlayerData(pending.playerName(), "0.0.0.0", discordId);
                } else {
                    if (linkedDiscordId != null && !linkedDiscordId.isBlank() && !linkedDiscordId.equals(discordId)) {
                        event.getHook().sendMessage(ConfigManager.msg().auth.alreadyRegistered).queue();
                        return;
                    }
                    db.setPlayerDiscordId(playerId, discordId);
                }
            } catch (SQLiteManager.DiscordAlreadyLinkedException e) {
                event.getHook().sendMessage(ConfigManager.msg().auth.alreadyRegistered).queue();
                return;
            }

            if (Server.getServer().getPlayerManager().isWhitelistEnabled()) {
                whitelistUser(pending.playerName());
            }

            event.getHook().sendMessage(ConfigManager.msg().auth.registrationSuccess).queue();
        });
    }

    public static String createLinkNonce(UUID playerUuid, String playerName) {
        cleanupExpiredLinkNonces();
        String nonce = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + LINK_NONCE_TTL_MILLIS;
        pendingLinkNonces.put(nonce, new PendingLinkNonce(playerUuid, playerName, expiresAt));
        return nonce;
    }

    private static void cleanupExpiredLinkNonces() {
        long now = System.currentTimeMillis();
        pendingLinkNonces.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }

    private void whitelistUser(String username) {
        MinecraftServer server = Server.getServer();
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
                Server.getPluginLogger().error("Failed to save whitelist after adding player: {}", Utils.sanitizeLog(username), e);
                return;
            }

            Server.getPluginLogger().info("Player {} added to whitelist (UUID: {})", Utils.sanitizeLog(username), profile.getId());
        } else {
            Server.getPluginLogger().debug("Player {} is already in whitelist", Utils.sanitizeLog(username));
        }
    }

    /**
     * Handles the unlink command from Discord.
     * Unlinks a Discord account from the Minecraft account if enabled in config.
     */
    public void unlinkAccount(SlashCommandInteractionEvent event) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            event.reply("Authentication service unavailable.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        if (!ConfigManager.conf().discord.allowUserUnlink) {
            event.getHook().sendMessage(ConfigManager.msg().discord.unlinkNotAllowed).queue();
            return;
        }

        String discordId = event.getUser().getId();
        Object playerId = db.getPlayerIdByDiscordId(discordId);
        
        if (playerId == null) {
            event.getHook().sendMessage(ConfigManager.msg().auth.notRegistered).setEphemeral(true).queue();
            return;
        }

        if (Server.getServer() == null) {
            event.getHook().sendMessage("Authentication service unavailable.").queue();
            return;
        }

        String playerName = db.getPlayerName(playerId);
        withPlayerOnlineCheck(playerName, online -> {
            if (online) {
                event.getHook().sendMessage(ConfigManager.msg().discord.unlinkWhileOnline).queue();
                return;
            }

            db.deletePlayerData(playerId);
            event.getHook().sendMessage(ConfigManager.msg().discord.accountUnlinked.formatted(event.getUser().getName())).queue();
        });

    }

    public void checkStatus(SlashCommandInteractionEvent event) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            event.reply("Authentication service unavailable.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        String discordId = event.getUser().getId();
        Object playerId = db.getPlayerIdByDiscordId(discordId);
        if (playerId == null) {
            event.getHook().sendMessage(ConfigManager.msg().auth.notRegistered).setEphemeral(true).queue();
            return;
        }

        event.getHook().sendMessage(ConfigManager.msg().discord.accountLinkStatus.formatted(event.getUser().getName(), db.getPlayerName(playerId))).queue();
    }

    /**
     * Sends a confirmation message to a Discord user for login approval.

     * Includes player's IP and approval/rejection buttons.
     * 
     * @param playerId Database ID of the player requesting login
     */
    public void sendConfirm(Object playerId, String playerIp) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            Server.getPluginLogger().warn("Failed to send confirmation message: authentication service unavailable");
            return;
        }

        try {
            String discordId = db.getPlayerDiscordId(playerId);
            if (discordId == null) return;

            String playerName = db.getPlayerName(playerId);
            String token = registerPendingButton(playerId, discordId);
            AtomicBoolean requestFinished = new AtomicBoolean(false);
            ScheduledFuture<?> timeoutTask = discordApiTimeoutExecutor.schedule(() -> {
                if (requestFinished.compareAndSet(false, true)) {
                    pendingButtonActions.remove(token);
                    failClosedPendingAuth(playerId);
                }
            }, 5, TimeUnit.SECONDS);

            builder.retrieveUserById(discordId).queue(user -> {
                if (user == null) {
                    onConfirmDispatchFailure(playerId, token, requestFinished, timeoutTask, null);
                    return;
                }

                user.openPrivateChannel().queue(channel -> channel.sendMessage(
                                String.format(
                                        ConfigManager.msg().discord.discordLoginRequest,
                                        playerName,
                                        playerIp
                                )
                        )
                        .setActionRow(
                                Button.success(ACTION_APPROVE + ":" + token, ConfigManager.msg().discord.loginApproved),
                                Button.danger(ACTION_REJECT + ":" + token, ConfigManager.msg().discord.loginRejected)
                        ).queue(
                                success -> {
                                    if (requestFinished.compareAndSet(false, true)) {
                                        timeoutTask.cancel(false);
                                    }
                                },
                                error -> onConfirmDispatchFailure(playerId, token, requestFinished, timeoutTask, error)
                        ),
                        error -> onConfirmDispatchFailure(playerId, token, requestFinished, timeoutTask, error)
                );
            }, error -> onConfirmDispatchFailure(playerId, token, requestFinished, timeoutTask, error));
        } catch (Exception e) {
            Server.getPluginLogger().error("Failed to send confirmation message", e);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        cleanupExpiredButtons();

        String[] parts = event.getComponentId().split(":", 2);
        if (parts.length != 2) {
            event.reply(ConfigManager.msg().discord.invalidOrExpiredRequest).setEphemeral(true).queue();
            return;
        }

        String action = parts[0];
        String token = parts[1];

        PendingButtonAction pending = pendingButtonActions.get(token);
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            pendingButtonActions.remove(token);
            event.editMessage(ConfigManager.msg().discord.invalidOrExpiredRequest).setComponents().queue();
            return;
        }

        if (!pending.discordId().equals(event.getUser().getId())) {
            event.reply(ConfigManager.msg().discord.notYourLoginRequest).setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case ACTION_APPROVE: {
                pendingButtonActions.remove(token);
                Server.getServer().execute(() -> {
                    ServerPlayerEntity player = getPendingPlayer(pending.playerId());
                    if (player == null || player.isDisconnected()) {
                        event.editMessage(ConfigManager.msg().discord.playerOfflineRequest).setComponents().queue();
                        return;
                    }

                    AuthOrchestrator.onDiscordApprove(player, pending.playerId(), event.getUser().getId(), result -> {
                        switch (result) {
                            case APPROVED -> {
                                event.editMessage(ConfigManager.msg().discord.loginApprovedMessage).setComponents().queue();
                                Server.getPluginLogger().info("Login approved for player: {}", Utils.sanitizeLog(player.getName().getString()));
                            }
                            case ROLE_DENIED -> {
                                event.editMessage(ConfigManager.msg().auth.missingRequiredDiscordRole).setComponents().queue();
                                Server.getPluginLogger().info("Login denied by role check for player: {}", Utils.sanitizeLog(player.getName().getString()));
                            }
                            case NOT_PENDING -> event.editMessage(ConfigManager.msg().discord.alreadyHandledRequest).setComponents().queue();
                        }
                    });
                });
                break;
            }
            case ACTION_REJECT:
                pendingButtonActions.remove(token);
                Server.getServer().execute(() -> {
                    ServerPlayerEntity player = getPendingPlayer(pending.playerId());
                    if (player == null || player.isDisconnected()) {
                        event.editMessage(ConfigManager.msg().discord.playerOfflineRequest).setComponents().queue();
                        return;
                    }

                    if (!PlayerAuthManager.isPendingAuth(player)) {
                        event.editMessage(ConfigManager.msg().discord.alreadyHandledRequest).setComponents().queue();
                        return;
                    }

                    AuthOrchestrator.onDiscordReject(player);
                    event.editMessage(ConfigManager.msg().discord.loginRejectedMessage).setComponents().queue();
                    Server.getPluginLogger().info("Login rejected for player: {}", Utils.sanitizeLog(player.getName().getString()));
                });
                break;
            default:
                event.reply(ConfigManager.msg().discord.unknownButton).setEphemeral(true).queue();
                break;
        }
    }

    private String registerPendingButton(Object playerId, String discordId) {
        cleanupExpiredButtons();
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + (ConfigManager.conf().loginTimer.loginTime * 1000L);
        pendingButtonActions.put(token, new PendingButtonAction(playerId, discordId, expiresAt));
        return token;
    }

    private void cleanupExpiredButtons() {
        long now = System.currentTimeMillis();
        pendingButtonActions.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }

    private ServerPlayerEntity getPendingPlayer(Object playerId) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            return null;
        }
        try {
            return Server.getPlayerByName(db.getPlayerName(playerId));
        } catch (Exception e) {
            Server.getPluginLogger().error("Error getting player by name", e);
            return null;
        }
    }

    private void withPlayerOnlineCheck(UUID playerUuid, Consumer<Boolean> callback) {
        if (Server.getServer() == null) {
            callback.accept(false);
            return;
        }

        Server.getServer().execute(() -> callback.accept(
                Server.getServer().getPlayerManager().getPlayer(playerUuid) != null
        ));
    }

    private void withPlayerOnlineCheck(String playerName, Consumer<Boolean> callback) {
        if (playerName == null || playerName.isBlank() || Server.getServer() == null) {
            callback.accept(false);
            return;
        }

        Server.getServer().execute(() -> callback.accept(
                Server.getServer().getPlayerManager().getPlayer(playerName) != null
        ));
    }

    private void onConfirmDispatchFailure(Object playerId, String token, AtomicBoolean requestFinished, ScheduledFuture<?> timeoutTask, Throwable error) {
        if (!requestFinished.compareAndSet(false, true)) {
            return;
        }
        timeoutTask.cancel(false);
        pendingButtonActions.remove(token);
        if (error != null) {
            Server.getPluginLogger().warn("Failed to dispatch Discord confirmation request", error);
        }
        failClosedPendingAuth(playerId);
    }

    private void failClosedPendingAuth(Object playerId) {
        Server.getServer().execute(() -> {
            ServerPlayerEntity player = getPendingPlayer(playerId);
            if (player == null || player.isDisconnected() || !PlayerAuthManager.isPendingAuth(player)) {
                return;
            }
            player.sendMessage(net.minecraft.text.Text.literal(ConfigManager.msg().auth.authenticationFailed), false);
            PlayerAuthManager.kickPlayer(player);
        });
    }

    public static JDA getJda() {
        return builder;
    }

    public boolean isTokenCorrupted() {
        return tokenCorrupted;
    }

    public void shutdown() {
        if (builder != null) {
            builder.shutdown();
        }
        discordApiTimeoutExecutor.shutdownNow();
    }
}
