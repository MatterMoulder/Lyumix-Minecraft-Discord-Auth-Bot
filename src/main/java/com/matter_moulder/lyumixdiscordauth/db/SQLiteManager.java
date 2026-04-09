package com.matter_moulder.lyumixdiscordauth.db;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteErrorCode;
import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;

public class SQLiteManager implements DatabaseManager {
    public static final class DiscordAlreadyLinkedException extends RuntimeException {
        public DiscordAlreadyLinkedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private HikariDataSource dataSource;

    public SQLiteManager() {
        Path dbPath = Server.getModFolder().resolve("db.sqlite");
        boolean isSuccessfullyCreated;
        if (!dbPath.toFile().exists()) {
            try {
                isSuccessfullyCreated = dbPath.toFile().createNewFile();
            } catch (IOException e) {
                Server.getPluginLogger().error("Failed to create SQLite database file at path: {}", dbPath, e);
                return;
            }
        } else {
            isSuccessfullyCreated = true;
        }

        assert isSuccessfullyCreated : "Failed to create SQLite database file at path: " + dbPath;

        try {
            Class.forName("org.sqlite.JDBC");
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
            hikariConfig.setMaximumPoolSize(4);
            hikariConfig.setConnectionTimeout(3000);

            dataSource = new HikariDataSource(hikariConfig);
            createTable();
        } catch (Exception e) {
            Server.getPluginLogger().error("Failed to connect to SQLite database at path: {}", dbPath, e);
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
        }
    }


    private void createTable() {
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE," +
                    "authcode TEXT," +
                    "registered INTEGER DEFAULT 0," +
                    "discord_id TEXT UNIQUE," +
                    "ip TEXT," +
                    "last_login INTEGER," +
                    "refresh_token_hash TEXT," +
                    "expires_at BIGINT" +
                    ")");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_players_discord_id_unique ON players(discord_id) WHERE discord_id IS NOT NULL");

            // Legacy schema upgrade: old DBs may miss refresh_token_hash.
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN refresh_token_hash TEXT");
            } catch (SQLException e) {
                String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (!message.contains("duplicate column name")) {
                    throw e;
                }
            }

            try {
                stmt.execute("ALTER TABLE players ADD COLUMN expires_at BIGINT");
            } catch (SQLException e) {
                String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (!message.contains("duplicate column name")) {
                    throw e;
                }
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS secrets (" +
                    "k TEXT PRIMARY KEY," +
                    "v TEXT" +
                    ")");
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to initialize SQLite schema", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("SQLite data source is not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void savePlayerData(String name, String playersIp, String discordId) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO players (name, discord_id, ip, last_login) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, name);
            pstmt.setString(2, discordId);
            pstmt.setString(3, playersIp);
            pstmt.setLong(4, 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (e.getErrorCode() == SQLiteErrorCode.SQLITE_CONSTRAINT.code && message.contains("discord_id")) {
                throw new DiscordAlreadyLinkedException("Discord account already linked", e);
            }
            Server.getPluginLogger().error("Failed to save player data for player: {}", Utils.sanitizeLog(name), e);
        }
    }

    @Override
    public Object getPlayerIdByName(String name) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT id FROM players WHERE name = ?")) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player ID by name: {}", Utils.sanitizeLog(name), e);
        }
        return null;
    }

    @Override
    public Object getPlayerIdByDiscordId(String val) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT id FROM players WHERE discord_id = ?")) {
            pstmt.setString(1, val);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player ID by Discord ID: {}", Utils.sanitizeLog(val), e);
        }
        return null;
    }

    @Override
    public String getPlayerName(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT name FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer)id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player name by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public String getPlayerDiscordId(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT discord_id FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer)id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player Discord ID by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public Long getPlayerLastLoginTime(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT last_login FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer)id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_login");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player last login time by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public String getPlayerIp(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT ip FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer)id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ip");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player IP by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public String getPlayerRefreshTokenHash(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT refresh_token_hash FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer) id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("refresh_token_hash");
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player refresh token hash by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public Long getPlayerRefreshTokenExpiresAt(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT expires_at FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer) id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long value = rs.getLong("expires_at");
                    return rs.wasNull() ? null : value;
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to get player refresh token expiration by ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
        return null;
    }

    @Override
    public void setPlayerDiscordId(Object id, String value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET discord_id = ? WHERE id = ?")) {
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to set player Discord ID for player ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public void setPlayerIp(Object id, String value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET ip = ? WHERE id = ?")) {
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to set player IP for player ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public void setPlayerLastLoginTime(Object id, Long value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET last_login = ? WHERE id = ?")) {
            pstmt.setLong(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to set player last login time for player ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public void setPlayerRefreshTokenHash(Object id, String value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET refresh_token_hash = ? WHERE id = ?")) {
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to set player refresh token hash for ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public void setPlayerRefreshTokenExpiresAt(Object id, Long value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET expires_at = ? WHERE id = ?")) {
            if (value == null) {
                pstmt.setNull(1, java.sql.Types.BIGINT);
            } else {
                pstmt.setLong(1, value);
            }
            pstmt.setInt(2, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to set player refresh token expiration for ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public boolean setRefreshToken(Object id, String newHash, long expiresAt) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET refresh_token_hash = ?, expires_at = ? WHERE id = ?")) {
            pstmt.setString(1, newHash);
            pstmt.setLong(2, expiresAt);
            pstmt.setInt(3, (Integer) id);
            return pstmt.executeUpdate() == 1;
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to atomically rotate refresh token for ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
            return false;
        }
    }

    @Override
    public boolean consumeRefreshTokenIfValid(Object id, String expectedHash, long nowMillis) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(
                     "UPDATE players SET refresh_token_hash = NULL, expires_at = NULL WHERE id = ? AND refresh_token_hash = ? AND expires_at >= ?")) {
            pstmt.setInt(1, (Integer) id);
            pstmt.setString(2, expectedHash);
            pstmt.setLong(3, nowMillis);
            return pstmt.executeUpdate() == 1;
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to consume refresh token for ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
            return false;
        }
    }

    @Override
    public void clearPlayerRefreshTokenHash(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET refresh_token_hash = NULL, expires_at = NULL WHERE id = ?")) {
            pstmt.setInt(1, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to clear player refresh token hash for ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public String getSecret(String key) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("SELECT v FROM secrets WHERE k = ?")) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return SecretCryptoService.decrypt(rs.getString("v"));
                }
            }
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to read secret {}", Utils.sanitizeLog(key), e);
        }
        return null;
    }

    @Override
    public void setSecret(String key, String value) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("INSERT INTO secrets (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v")) {
            pstmt.setString(1, key);
            pstmt.setString(2, SecretCryptoService.encrypt(value));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to store secret {}", Utils.sanitizeLog(key), e);
        }
    }

    @Override
    public void clearSecret(String key) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("DELETE FROM secrets WHERE k = ?")) {
            pstmt.setString(1, key);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to clear secret {}", Utils.sanitizeLog(key), e);
        }
    }

    @Override
    public void deletePlayerData(Object id) {
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement("DELETE FROM players WHERE id = ?")) {
            pstmt.setInt(1, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Server.getPluginLogger().error("Failed to delete player data for player ID: {}", Utils.sanitizeLog(String.valueOf(id)), e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception e) {
                Server.getPluginLogger().error("Failed to close SQLite database connection pool", e);
            }
        }
    }
}