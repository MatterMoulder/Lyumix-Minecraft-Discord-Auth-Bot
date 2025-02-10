package com.matter_moulder.lyumixdiscordauth.db;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.matter_moulder.lyumixdiscordauth.Main;

public class SQLiteManager implements DatabaseManager {
    private Connection connection;

    public SQLiteManager() {
        Path dbPath = Main.getModFolder().resolve("db.sqlite");
        if (!dbPath.toFile().exists()) {
            try {
                dbPath.toFile().createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTable();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void createTable() {
        try {
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE," +
                    "authcode TEXT," +
                    "registered INTEGER DEFAULT 0," +
                    "discord_id TEXT," +
                    "ip TEXT," +
                    "last_login INTEGER" +
                    ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void savePlayerData(String name, String playersIp, String discordId) {
        try {
            PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO players (name, discord_id, ip, last_login) VALUES (?, ?, ?, ?)");

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
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT id FROM players WHERE name = ?");
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
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT id FROM players WHERE discord_id = ?");
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
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT name FROM players WHERE id = ?");
            pstmt.setInt(1, (Integer)id);
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
    public String getPlayerDiscordId(Object id) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT discord_id FROM players WHERE id = ?");
            pstmt.setInt(1, (Integer)id);
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
    public Long getPlayerLastLoginTime(Object id) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT last_login FROM players WHERE id = ?");
            pstmt.setInt(1, (Integer)id);
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
    public String getPlayerIp(Object id) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT ip FROM players WHERE id = ?");
            pstmt.setInt(1, (Integer)id);
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
        try {
            PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET discord_id = ? WHERE id = ?");
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPlayerIp(Object id, String value) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET ip = ? WHERE id = ?");
            pstmt.setString(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setPlayerLastLoginTime(Object id, Long value) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("UPDATE players SET last_login = ? WHERE id = ?");
            pstmt.setLong(1, value);
            pstmt.setInt(2, (Integer)id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deletePlayerData(Object id) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("DELETE FROM players WHERE id = ?");
            pstmt.setInt(1, (Integer)id);
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