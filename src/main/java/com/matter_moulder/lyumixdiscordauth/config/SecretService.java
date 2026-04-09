package com.matter_moulder.lyumixdiscordauth.config;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;

public final class SecretService {
    public static final String SECRET_BOT_TOKEN = "discord.botToken";
    public static final String SECRET_CLIENT_ID = "discord.clientId";
    public static final String SECRET_CLIENT_SECRET = "discord.clientSecret";

    private SecretService() {
    }

    public static synchronized void syncSecretsFromConfig() {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            return;
        }

        boolean changed = false;
        changed |= upsertIfVisible(db, SECRET_BOT_TOKEN, ConfigManager.conf().discord.botToken);
        changed |= upsertIfVisible(db, SECRET_CLIENT_ID, ConfigManager.conf().discord.discordClientId);
        changed |= upsertIfVisible(db, SECRET_CLIENT_SECRET, ConfigManager.conf().discord.discordClientSecret);

        if (!isMasked(ConfigManager.conf().discord.botToken) && db.getSecret(SECRET_BOT_TOKEN) != null) {
            ConfigManager.conf().discord.botToken = mask(db.getSecret(SECRET_BOT_TOKEN));
            changed = true;
        }
        if (!isMasked(ConfigManager.conf().discord.discordClientId) && db.getSecret(SECRET_CLIENT_ID) != null) {
            ConfigManager.conf().discord.discordClientId = mask(db.getSecret(SECRET_CLIENT_ID));
            changed = true;
        }
        if (!isMasked(ConfigManager.conf().discord.discordClientSecret) && db.getSecret(SECRET_CLIENT_SECRET) != null) {
            ConfigManager.conf().discord.discordClientSecret = mask(db.getSecret(SECRET_CLIENT_SECRET));
            changed = true;
        }

        if (changed) {
            try {
                ConfigManager.saveConfig();
            } catch (Exception e) {
                Server.getPluginLogger().error("Failed to save masked config after secret sync", e);
            }
        }
    }

    public static String getBotToken() {
        return getSecretOrEmpty(SECRET_BOT_TOKEN);
    }

    public static String getClientId() {
        return getSecretOrEmpty(SECRET_CLIENT_ID);
    }

    public static String getClientSecret() {
        return getSecretOrEmpty(SECRET_CLIENT_SECRET);
    }

    public static boolean hasSecret(String key) {
        return !getSecretOrEmpty(key).isBlank();
    }

    public static String getMaskedSecret(String key) {
        return mask(getSecretOrEmpty(key));
    }

    public static boolean upsertSecretIfProvided(String key, String value) {
        DatabaseManager db = Server.getDatabase();
        if (db == null || value == null || value.isBlank() || isMasked(value)) {
            return false;
        }

        String existing = db.getSecret(key);
        if (!value.equals(existing)) {
            db.setSecret(key, value);
            return true;
        }

        return false;
    }

    private static boolean upsertIfVisible(DatabaseManager db, String key, String value) {
        if (value == null || value.isBlank() || isMasked(value)) {
            return false;
        }

        String existing = db.getSecret(key);
        if (!value.equals(existing)) {
            db.setSecret(key, value);
        }
        return true;
    }

    private static String getSecretOrEmpty(String key) {
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            return "";
        }

        String value = db.getSecret(key);
        return value == null ? "" : value;
    }

    public static boolean isMasked(String value) {
        return value != null && value.startsWith("****");
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int reveal = Math.min(4, value.length());
        return "****" + value.substring(value.length() - reveal);
    }
}

