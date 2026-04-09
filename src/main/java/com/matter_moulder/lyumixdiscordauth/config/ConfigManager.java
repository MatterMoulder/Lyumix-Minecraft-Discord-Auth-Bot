package com.matter_moulder.lyumixdiscordauth.config;

import com.matter_moulder.lyumixdiscordauth.Server;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class ConfigManager {
    private static final Logger LOGGER = Server.LOGGER;
    private static final Path CONFIG_FILE = Server.getModFolder().resolve("config.hocon");
    private static final Path MESSAGES_FILE = Server.getModFolder().resolve("messages.hocon");
    private static Config config = new Config();
    private static Messages messages = new Messages();

    private ConfigManager() {
    }

    public static void load() throws Exception {
        LOGGER.info("Loading configuration...");
        loadMainConfig();
        loadMessages();
    }

    public static void saveConfig() throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(CONFIG_FILE).build();
        CommentedConfigurationNode rootNode = loader.createNode();
        rootNode.set(Config.class, config);
        loader.save(rootNode);
    }

    private static void loadMainConfig() throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(CONFIG_FILE).build();

        if (!CONFIG_FILE.toFile().exists()) {
            LOGGER.info("Creating configuration file!");
            CommentedConfigurationNode rootNode = loader.createNode();
            rootNode.set(Config.class, new Config());
            loader.save(rootNode);
            config = rootNode.get(Config.class, new Config());
            return;
        }

        CommentedConfigurationNode rootNode = loader.load();

//        removeOrphanKeys(rootNode, Config.class);
//        removeOrphanKeys(rootNode.node("database"), Config.DatabaseConfig.class);
//        removeOrphanKeys(rootNode.node("discord"), Config.DiscordConfig.class);
//        removeOrphanKeys(rootNode.node("login"), Config.LoginConfig.class);
//        removeOrphanKeys(rootNode.node("login-timer"), Config.TimerConfig.class);

        config = rootNode.get(Config.class, new Config());
        rootNode.set(Config.class, config);
        loader.save(rootNode);
    }

    private static String camelToKebab(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private static void loadMessages() throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(MESSAGES_FILE).build();

        if (!MESSAGES_FILE.toFile().exists()) {
            LOGGER.info("Creating messages file!");
            CommentedConfigurationNode rootNode = loader.createNode();
            rootNode.set(Messages.class, new Messages());
            loader.save(rootNode);
            messages = rootNode.get(Messages.class, new Messages());
            return;
        }

        CommentedConfigurationNode rootNode = loader.load();

//        removeOrphanKeys(rootNode, Messages.class);
//        removeOrphanKeys(rootNode.node("auth"), Messages.AuthMessages.class);
//        removeOrphanKeys(rootNode.node("admin"), Messages.AdminMessages.class);
//        removeOrphanKeys(rootNode.node("discord"), Messages.DiscordMessages.class);

        messages = rootNode.get(Messages.class, new Messages());
        rootNode.set(Messages.class, messages);
        loader.save(rootNode);
    }

    private static void removeOrphanKeys(CommentedConfigurationNode fileNode, Class<?> configClass) {
        Set<String> classKeys = Arrays.stream(configClass.getDeclaredFields())
                .map(f -> camelToKebab(f.getName()))
                .collect(Collectors.toSet());

        fileNode.childrenMap().forEach((key, child) -> {
            if (!classKeys.contains(key.toString())) {
                LOGGER.warn("Removing unknown config key '{}' — no longer used", key);
                child.raw(null);
            }
        });
    }

    public static Config conf() {
        return config;
    }

    public static Messages msg() {
        return messages;
    }
}