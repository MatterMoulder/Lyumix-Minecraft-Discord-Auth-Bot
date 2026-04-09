package com.matter_moulder.lyumixdiscordauth.db;

import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;

/**
 * Abstraction for player and secret persistence backends.
 */
public interface DatabaseManager {
    void savePlayerData(String name, String playersIp, String discordId);
    Object getPlayerIdByName(String name);
    Object getPlayerIdByDiscordId(String val);
    String getPlayerName(Object id);
    Long getPlayerLastLoginTime(Object id);
    String getPlayerDiscordId(Object id);
    String getPlayerIp(Object id);
    String getPlayerRefreshTokenHash(Object id);
    Long getPlayerRefreshTokenExpiresAt(Object id);
    void setPlayerDiscordId(Object id, String value);
    void setPlayerIp(Object id, String value);
    void setPlayerLastLoginTime(Object id, Long value);
    void setPlayerRefreshTokenHash(Object id, String value);
    void setPlayerRefreshTokenExpiresAt(Object id, Long value);
    boolean setRefreshToken(Object id, String newHash, long expiresAt);
    boolean consumeRefreshTokenIfValid(Object id, String expectedHash, long nowMillis);
    void clearPlayerRefreshTokenHash(Object id);
    String getSecret(String key);
    void setSecret(String key, String value);
    void clearSecret(String key);
    void deletePlayerData(Object id);

    void close();

    /**
     * Creates the configured database implementation.
     */
    static DatabaseManager create() {
        try {
            if (!"sqlite".equalsIgnoreCase(ConfigManager.conf().database.type)) {
                throw new IllegalArgumentException("Only sqlite is supported by this build");
            }
            return new SQLiteManager();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to database. Please check your connection string and database availability. Error: " + e.getMessage(), e);
        }
    }
}
