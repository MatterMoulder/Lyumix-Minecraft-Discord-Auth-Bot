package com.matter_moulder.lyumixdiscordauth;

import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;
import com.matter_moulder.lyumixdiscordauth.discord.BotMngr;
import com.matter_moulder.lyumixdiscordauth.handlers.DenyHandle;
import com.matter_moulder.lyumixdiscordauth.timer.Timer;
import com.matter_moulder.lyumixdiscordauth.commands.CommandManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Main entry point for the Discord Authentication mod.
 * Handles initialization of the bot, database, and event listeners.
 */
public class Main implements ModInitializer {
	public static final String MOD_ID = "lyumix-discord-auth";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private boolean isActive = false;

    private static DatabaseManager dbManager;

    private static final Timer LOGIN_TIMER = new Timer();

    private static MinecraftServer serverInstance;
    private static BotMngr bot;

    @Override
    public void onInitialize() {
        Path cacheDir = getModFolder().resolve("sessions/");

        try {
            initializeConfig();
            initializeDatabase();
            initializeBot();
            registerEventHandlers();
            registerCommands();
            if (!cacheDir.toFile().exists()) {
                Files.createDirectory(cacheDir);
            }
            
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
        getModFolder().toFile().mkdirs();
        ConfigMngr.load();
        LOGGER.info("Configuration loaded successfully!");
    }

    private void initializeDatabase() {
        try {
            dbManager = DatabaseManager.create();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to database, shutting down the mod...\nError: ", e);
            dbManager = null;
        }
    }

    private void initializeBot() {
        if (ConfigMngr.conf().discord.botToken.isEmpty()) {
            LOGGER.error("Bot token is empty, shutting down the mod...");
            return;
        }

        try {
            bot = new BotMngr();
        } catch (Exception e) {
            LOGGER.error("Failed to create bot manager, shutting down the mod...\nError: ", e);
            bot = null;
        }
    }

    private void registerEventHandlers() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) -> DenyHandle.onAnyBoolAction(player));
        UseBlockCallback.EVENT.register((player, world, hand, blockHitResult) -> DenyHandle.onAnyAction(player));
        UseItemCallback.EVENT.register((player, world, hand) -> DenyHandle.onUseItem(player));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, entityHitResult) -> DenyHandle.onAnyAction(player));
        UseEntityCallback.EVENT.register((player, world, hand, entity, entityHitResult) -> DenyHandle.onAnyAction(player));

        ServerLifecycleEvents.SERVER_STOPPED.register(this::onStopServer);

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            LOGIN_TIMER.onServerTick(server);
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandManager.register(dispatcher);
        });
    }

    public static ServerPlayerEntity getPlayerByName(String name) {
        return serverInstance.getPlayerManager().getPlayer(name);
    }

    public static Logger getPluginLogger() {
        return LOGGER;
    }

    public static BotMngr getDsBot() {
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
        if (dbManager != null) {
            dbManager.close();
        }
    }
}