package com.matter_moulder.lyumixdiscordauth.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgreDBManager implements DatabaseManager {
    private Connection connection;

    public PostgreDBManager(String connectionString, String username, String password) {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(connectionString, username, password);
            initializeTable();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void initializeTable() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS players (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                discord_id VARCHAR(255),
                ip VARCHAR(255),
                last_login BIGINT
            )
        """;

        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    @Override
    public void savePlayerData(String name, String playersIp, String discordId) {
        String sql = "INSERT INTO players (name, discord_id, ip, last_login) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, discordId);
            pstmt.setString(3, playersIp);
            pstmt.setLong(4, 0);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Object getPlayerIdByName(String name) {
        String sql = "SELECT id FROM players WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Object getPlayerIdByDiscordId(String val) {
        String sql = "SELECT id FROM players WHERE discord_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, val);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getPlayerName(Object id) {
        String sql = "SELECT name FROM players WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Long getPlayerLastLoginTime(Object id) {
        String sql = "SELECT last_login FROM players WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_login");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getPlayerDiscordId(Object id) {
        String sql = "SELECT discord_id FROM players WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("discord_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getPlayerIp(Object id) {
        String sql = "SELECT ip FROM players WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("ip");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setPlayerDiscordId(Object id, String value) {
        String sql = "UPDATE players SET discord_id = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPlayerIp(Object id, String value) {
        String sql = "UPDATE players SET ip = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPlayerLastLoginTime(Object id, Long value) {
        String sql = "UPDATE players SET last_login = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, value);
            pstmt.setInt(2, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deletePlayerData(Object id) {
        String sql = "DELETE FROM players WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, (Integer) id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
