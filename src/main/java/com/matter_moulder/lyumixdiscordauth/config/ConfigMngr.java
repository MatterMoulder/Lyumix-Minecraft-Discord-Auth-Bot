package com.matter_moulder.lyumixdiscordauth.config;

import com.matter_moulder.lyumixdiscordauth.Main;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class ConfigMngr {
    private static final Logger LOGGER = Main.LOGGER;
    private static final Path CONFIG_FILE = Main.getModFolder().resolve("config.hocon");
    private static final Path MESSAGES_FILE = Main.getModFolder().resolve("messages.hocon");
    private static Config config = new Config();
    private static Messages messages = new Messages();

    private ConfigMngr() {
    }

    public static void load() throws Exception {
        LOGGER.info("Loading configuration...");
        loadMainConfig();
        loadMessages();
    }

    private static void loadMainConfig() throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(CONFIG_FILE).build();
        CommentedConfigurationNode rootNode = loader.load();
        if (!CONFIG_FILE.toFile().exists()) {
            LOGGER.info("Creating configuration file!");
            rootNode.set(Config.class, new Config());
            loader.save(rootNode);
        }
        config = rootNode.get(Config.class, new Config());
    }

    private static void loadMessages() throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(MESSAGES_FILE).build();
        CommentedConfigurationNode rootNode = loader.load();
        if (!MESSAGES_FILE.toFile().exists()) {
            LOGGER.info("Creating messages file!");
            rootNode.set(Messages.class, new Messages());
            loader.save(rootNode);
        }
        messages = rootNode.get(Messages.class, new Messages());
    }

    public static Config conf() {
        return config;
    }

    public static Messages msg() {
        return messages;
    }
}