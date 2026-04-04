package com.matter_moulder.lyumixdiscordauth.db;

import com.matter_moulder.lyumixdiscordauth.config.ConfigMngr;

public interface DatabaseManager {
    void savePlayerData(String name, String playersIp, String discordId);
    Object getPlayerIdByName(String name);
    Object getPlayerIdByDiscordId(String val);
    String getPlayerName(Object id);
    Long getPlayerLastLoginTime(Object id);
    String getPlayerDiscordId(Object id);
    String getPlayerIp(Object id);
    void setPlayerDiscordId(Object id, String value);
    void setPlayerIp(Object id, String value);
    void setPlayerLastLoginTime(Object id, Long value);
    void deletePlayerData(Object id);
    void close();

    static DatabaseManager create() {
        try {
            return switch (ConfigMngr.conf().database.type) {
                case "mongodb" -> {
                    if (ConfigMngr.conf().database.connectionString.isEmpty()) {
                        throw new IllegalArgumentException("MongoDB connection string is empty in config.hocon. Please add connection string or delete config.hocon to generate new one (save Discord bot token before deleting)");
                    }
                    yield new MongoDBManager(ConfigMngr.conf().database.connectionString);
                }
                case "postgresql" -> {
                    if (ConfigMngr.conf().database.connectionString.isEmpty() || ConfigMngr.conf().database.username.isEmpty() || ConfigMngr.conf().database.password.isEmpty()) {

                        throw new IllegalArgumentException("PostgreSQL connection string is empty in config.hocon. Please add connection string or delete config.hocon to generate new one (save Discord bot token before deleting)");
                    }
                    yield new PostgreDBManager(ConfigMngr.conf().database.connectionString, ConfigMngr.conf().database.username, ConfigMngr.conf().database.password);
                }
                case "sqlite" -> new SQLiteManager();
                default -> throw new IllegalArgumentException("Unsupported database type: " + ConfigMngr.conf().database.type + " in config.hocon. Please check database type or delete config.hocon to generate new one (save Discord bot token before deleting)");
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to database. Please check your connection string and database availability. Error: " + e.getMessage(), e);
        }
    }

}

