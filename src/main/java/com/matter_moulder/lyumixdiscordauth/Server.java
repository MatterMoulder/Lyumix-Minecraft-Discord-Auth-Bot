package com.matter_moulder.lyumixdiscordauth;

import com.matter_moulder.lyumixdiscordauth.auth.AuthOrchestrator;
import com.matter_moulder.lyumixdiscordauth.auth.ModPackets;
import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.auth.RefreshTokenService;
import com.matter_moulder.lyumixdiscordauth.oauth.OAuthCallbackServer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.config.SecretService;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.discord.DiscordBotManager;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import com.matter_moulder.lyumixdiscordauth.commands.CommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Main entry point for the Discord Authentication mod.
 * Handles initialization of the bot, database, and event listeners.
 */
public class Server implements DedicatedServerModInitializer {
	public static final String MOD_ID = "lyumix-discord-auth";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private boolean isActive = false;

    private static DatabaseManager dbManager;

    private static final Timer LOGIN_TIMER = new Timer();

    private static MinecraftServer serverInstance;
    private static DiscordBotManager bot;

    @Override
    public void onInitializeServer() {
        Path cacheDir = getModFolder().resolve("sessions/");

        try {
            Files.createDirectories(cacheDir);
            initializeConfig();
            initializeDatabase();
            initializeBot();
            initializeOAuthCallbackServer();
            registerPacketHandlers();
            registerEventHandlers();
            registerCommands();
            
            LOGGER.info("Lyumix Discord Auth initialized successfully!");
        } catch (IOException e) {
            LOGGER.error("Failed to create cache directories, shutting down the mod...\nError: ", e);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize mod: ", e);
        } finally {
            isActive = true;
        }
    }

    private void initializeConfig() throws Exception {
        Files.createDirectories(getModFolder());
        ConfigManager.load();
        LOGGER.info("Configuration loaded successfully!");
    }

    private void initializeDatabase() {
        try {
            dbManager = DatabaseManager.create();
            SecretService.syncSecretsFromConfig();
            RefreshTokenService.migrateLegacyStoreIfNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to database, shutting down the mod...\nError: ", e);
            dbManager = null;
        }
    }

    private void initializeBot() {
        if (SecretService.getBotToken().isEmpty()) {
            LOGGER.error("Bot token is empty, shutting down the mod...");
            return;
        }

        try {
            bot = new DiscordBotManager();
        } catch (Exception e) {
            LOGGER.error("Failed to create bot manager, shutting down the mod...\nError: ", e);
            bot = null;
        }
    }

    private void registerPacketHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.PROBE_RESPONSE_ID,
                (server, player, handler, buf, responseSender) -> {
                    String refreshToken = "";
                    String clientBindingHash = "";
                    String clientMachineIdHash = "";
                    String clientServerAddress = "";
                    if (buf.isReadable()) {
                        try {
                            refreshToken = buf.readString(1024);
                            if (buf.isReadable()) {
                                clientBindingHash = buf.readString(256);
                            }
                            if (buf.isReadable()) {
                                clientMachineIdHash = buf.readString(256);
                            }
                            if (buf.isReadable()) {
                                clientServerAddress = buf.readString(512);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Malformed probe response string from player {}", Utils.sanitizeLog(player.getName().getString()));
                            return;
                        }
                    }
                    String finalRefreshToken = refreshToken;
                    String finalClientBindingHash = clientBindingHash;
                    String finalClientMachineIdHash = clientMachineIdHash;
                    String finalClientServerAddress = clientServerAddress;
                    server.execute(() -> AuthOrchestrator.onProbeDetected(player, finalRefreshToken, finalClientBindingHash, finalClientMachineIdHash, finalClientServerAddress));
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.ADMIN_CONFIG_OPEN_C2S_ID,
                (server, player, handler, buf, responseSender) -> server.execute(() -> sendAdminConfig(player))
        );

        ServerPlayNetworking.registerGlobalReceiver(ModPackets.ADMIN_CONFIG_SAVE_C2S_ID,
                (server, player, handler, buf, responseSender) -> {
                    if (!player.hasPermissionLevel(4)) return;
                    NbtCompound nbt;
                    try {
                        nbt = buf.readNbt(new NbtTagSizeTracker(2097152L));
                    } catch (Exception e) {
                        // Log a sanitized player name to avoid log-forging with malformed packet attempts.
                        LOGGER.warn("Malformed NBT from player {}", Utils.sanitizeLog(player.getName().getString()));
                        return;
                    }

                    server.execute(() -> handleAdminConfigSave(player, nbt));
                }
        );
    }

    private void sendAdminConfig(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(4)) {
            player.sendMessage(net.minecraft.text.Text.literal("No permission"), false);
            return;
        }

        var packet = PacketByteBufs.create();
        NbtCompound data = new NbtCompound();
        data.putBoolean("discord.useDiscordOAuth", ConfigManager.conf().discord.useDiscordOAuth);
        data.putString("discord.authFlowMode", ConfigManager.conf().discord.authFlowMode == null ? "legacy" : ConfigManager.conf().discord.authFlowMode);
        data.putBoolean("discord.roleCheckEnabled", ConfigManager.conf().discord.roleCheckEnabled);
        data.putBoolean("discord.allowUserUnlink", ConfigManager.conf().discord.allowUserUnlink);
        data.putBoolean("discord.requireAllRoles", ConfigManager.conf().discord.requireAllRoles);
        data.putString("discord.discordServerId", nullToEmpty(ConfigManager.conf().discord.discordServerId));
        data.putInt("discord.oauthCallbackServerPort", ConfigManager.conf().discord.oauthCallbackServerPort);
        data.putString("discord.discordOAuthRedirectUri", nullToEmpty(ConfigManager.conf().discord.discordOAuthRedirectUri));
        data.putString("discord.requiredRoleIdsCsv", String.join(",", ConfigManager.conf().discord.requiredRoleIds));
        data.putString("discord.botTokenMasked", SecretService.getMaskedSecret(SecretService.SECRET_BOT_TOKEN));
        data.putString("discord.clientIdMasked", SecretService.getMaskedSecret(SecretService.SECRET_CLIENT_ID));
        data.putString("discord.clientSecretMasked", SecretService.getMaskedSecret(SecretService.SECRET_CLIENT_SECRET));

        data.putBoolean("login.blindnessWhileLogin", ConfigManager.conf().login.blindnessWhileLogin);
        data.putInt("login.autoLoginTime", ConfigManager.conf().login.autoLoginTime);

        data.putBoolean("timer.enabled", ConfigManager.conf().loginTimer.enabled);
        data.putInt("timer.loginTime", ConfigManager.conf().loginTimer.loginTime);
        data.putString("timer.title", nullToEmpty(ConfigManager.conf().loginTimer.title));
        data.putString("timer.firstColor", nullToEmpty(ConfigManager.conf().loginTimer.firstColor));
        data.putString("timer.secondColor", nullToEmpty(ConfigManager.conf().loginTimer.secondColor));
        data.putString("timer.thirdColor", nullToEmpty(ConfigManager.conf().loginTimer.thirdColor));
        data.putInt("timer.secondTime", ConfigManager.conf().loginTimer.secondTime);
        data.putInt("timer.thirdTime", ConfigManager.conf().loginTimer.thirdTime);

        packet.writeNbt(data);
        if (!ServerPlayNetworking.canSend(player, ModPackets.ADMIN_CONFIG_S2C_ID)) {
            return;
        }
        ServerPlayNetworking.send(player, ModPackets.ADMIN_CONFIG_S2C_ID, packet);
    }

    private void handleAdminConfigSave(ServerPlayerEntity player, NbtCompound data) {
        if (!player.hasPermissionLevel(4)) {
            player.sendMessage(net.minecraft.text.Text.literal("No permission"), false);
            return;
        }

        String authFlowMode = nullToEmpty(data.getString("discord.authFlowMode")).trim().toLowerCase(Locale.ROOT);
        if (!isValidAuthFlowMode(authFlowMode)) {
            player.sendMessage(net.minecraft.text.Text.literal("Invalid auth flow mode"), false);
            return;
        }

        Integer oauthPort = parseIntInRange(data.getString("discord.oauthCallbackServerPort"), 1, 65535);
        Integer autoLoginTime = parseIntInRange(data.getString("login.autoLoginTime"), 0, 24 * 3650);
        Integer loginTime = parseIntInRange(data.getString("timer.loginTime"), 1, 3600);
        Integer secondTime = parseIntInRange(data.getString("timer.secondTime"), 0, 3600);
        Integer thirdTime = parseIntInRange(data.getString("timer.thirdTime"), 0, 3600);

        if (oauthPort == null || autoLoginTime == null || loginTime == null || secondTime == null || thirdTime == null) {
            player.sendMessage(net.minecraft.text.Text.literal("Invalid numeric values in admin config"), false);
            return;
        }
        if (!isValidColor(data.getString("timer.firstColor"))
                || !isValidColor(data.getString("timer.secondColor"))
                || !isValidColor(data.getString("timer.thirdColor"))) {
            player.sendMessage(net.minecraft.text.Text.literal("Invalid timer color. Use Minecraft formatting colors."), false);
            return;
        }
        if (secondTime < thirdTime) {
            player.sendMessage(net.minecraft.text.Text.literal("timer.secondTime must be >= timer.thirdTime"), false);
            return;
        }

        boolean oauthWasEnabled = AuthOrchestrator.isOAuthEnabledForCurrentMode();
        int oldOauthPort = ConfigManager.conf().discord.oauthCallbackServerPort;

        ConfigManager.conf().discord.useDiscordOAuth = data.getBoolean("discord.useDiscordOAuth");
        ConfigManager.conf().discord.authFlowMode = authFlowMode;
        ConfigManager.conf().discord.roleCheckEnabled = data.getBoolean("discord.roleCheckEnabled");
        ConfigManager.conf().discord.allowUserUnlink = data.getBoolean("discord.allowUserUnlink");
        ConfigManager.conf().discord.requireAllRoles = data.getBoolean("discord.requireAllRoles");
        ConfigManager.conf().discord.discordServerId = nullToEmpty(data.getString("discord.discordServerId")).trim();
        ConfigManager.conf().discord.oauthCallbackServerPort = oauthPort;
        ConfigManager.conf().discord.discordOAuthRedirectUri = nullToEmpty(data.getString("discord.discordOAuthRedirectUri")).trim();
        ConfigManager.conf().discord.requiredRoleIds = parseCsv(data.getString("discord.requiredRoleIdsCsv"));

        ConfigManager.conf().login.blindnessWhileLogin = data.getBoolean("login.blindnessWhileLogin");
        ConfigManager.conf().login.autoLoginTime = autoLoginTime;

        ConfigManager.conf().loginTimer.enabled = data.getBoolean("timer.enabled");
        ConfigManager.conf().loginTimer.loginTime = loginTime;
        ConfigManager.conf().loginTimer.title = nullToEmpty(data.getString("timer.title"));
        ConfigManager.conf().loginTimer.firstColor = data.getString("timer.firstColor").toUpperCase(Locale.ROOT);
        ConfigManager.conf().loginTimer.secondColor = data.getString("timer.secondColor").toUpperCase(Locale.ROOT);
        ConfigManager.conf().loginTimer.thirdColor = data.getString("timer.thirdColor").toUpperCase(Locale.ROOT);
        ConfigManager.conf().loginTimer.secondTime = secondTime;
        ConfigManager.conf().loginTimer.thirdTime = thirdTime;


        SecretService.upsertSecretIfProvided(SecretService.SECRET_BOT_TOKEN, data.getString("discord.botToken"));
        SecretService.upsertSecretIfProvided(SecretService.SECRET_CLIENT_ID, data.getString("discord.clientId"));
        SecretService.upsertSecretIfProvided(SecretService.SECRET_CLIENT_SECRET, data.getString("discord.clientSecret"));
        SecretService.syncSecretsFromConfig();

        try {
            ConfigManager.saveConfig();
            Timer.reloadConfig();
            reconfigureOAuthServer(oauthWasEnabled, oldOauthPort);
            restartDiscordBot();
            player.sendMessage(net.minecraft.text.Text.literal("LDA settings saved"), false);
        } catch (Exception e) {
            LOGGER.error("Failed to save config from admin panel", e);
            player.sendMessage(net.minecraft.text.Text.literal("Failed to save LDA settings"), false);
        }
    }

    private boolean isValidAuthFlowMode(String mode) {
        if (mode == null) {
            return false;
        }
        return mode.equals("legacy")
                || mode.equals("oauth_notify_fallback")
                || mode.equals("oauth_only")
                || mode.equals("notify_only");
    }

    private void reconfigureOAuthServer(boolean oauthWasEnabled, int oldOauthPort) {
        boolean oauthEnabledNow = AuthOrchestrator.isOAuthEnabledForCurrentMode();
        if (!oauthEnabledNow) {
            OAuthCallbackServer.stop();
            return;
        }

        if (oauthWasEnabled && oldOauthPort == ConfigManager.conf().discord.oauthCallbackServerPort) {
            return;
        }

        OAuthCallbackServer.stop();
        try {
            OAuthCallbackServer.start();
            LOGGER.info("OAuth callback server reconfigured on port {}", ConfigManager.conf().discord.oauthCallbackServerPort);
        } catch (Exception e) {
            LOGGER.error("Failed to restart OAuth callback server after config update", e);
        }
    }

    private void restartDiscordBot() {
        if (bot != null) {
            bot.shutdown();
            bot = null;
        }

        if (SecretService.getBotToken().isBlank()) {
            return;
        }

        try {
            bot = new DiscordBotManager();
        } catch (Exception e) {
            LOGGER.error("Failed to restart Discord bot after config update", e);
            bot = null;
        }
    }

    private static Integer parseIntInRange(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(nullToEmpty(value).trim());
            if (parsed < min || parsed > max) {
                return null;
            }
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isValidColor(String value) {
        try {
            Formatting formatting = Formatting.valueOf(nullToEmpty(value).trim().toUpperCase(Locale.ROOT));
            return formatting.isColor();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> parseCsv(String csv) {
        return Arrays.stream(nullToEmpty(csv).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }


    private void initializeOAuthCallbackServer() {
        if (!AuthOrchestrator.isOAuthEnabledForCurrentMode()) {
            return;
        }

        try {
            OAuthCallbackServer.start();
            LOGGER.info("OAuth callback server started on port {}", ConfigManager.conf().discord.oauthCallbackServerPort);
        } catch (Exception e) {
            LOGGER.error("Failed to start OAuth callback server", e);
        }
    }

    private void registerEventHandlers() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> serverInstance = server);

        PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) -> PlayerAuthManager.canAnyAction(player));
        UseBlockCallback.EVENT.register((player, world, hand, blockHitResult) -> PlayerAuthManager.onAnyAction(player));
        UseItemCallback.EVENT.register((player, world, hand) -> PlayerAuthManager.onUseItem(player));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, entityHitResult) -> PlayerAuthManager.onAnyAction(player));
        UseEntityCallback.EVENT.register((player, world, hand, entity, entityHitResult) -> PlayerAuthManager.onAnyAction(player));

        ServerLifecycleEvents.SERVER_STOPPED.register(this::onStopServer);

        ServerTickEvents.START_SERVER_TICK.register(LOGIN_TIMER::onServerTick);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CommandManager.register(dispatcher));
    }

    public static ServerPlayerEntity getPlayerByName(String name) {
        return serverInstance.getPlayerManager().getPlayer(name);
    }

    public static Logger getPluginLogger() {
        return LOGGER;
    }

    public static DiscordBotManager getDsBot() {
        return bot;
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }

    public static DatabaseManager getDatabase() {
        return dbManager;
    }

    public boolean isActive() {
        return isActive;
    }

    public static Path getModFolder() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }

    public static Path getSessionDir() {
        return getModFolder().resolve("sessions/");
    }

    private void onStopServer(MinecraftServer server) {
        if (bot != null) {
            bot.shutdown();
        }
        OAuthCallbackServer.stop();
        if (dbManager != null) {
            dbManager.close();
        }
    }
}