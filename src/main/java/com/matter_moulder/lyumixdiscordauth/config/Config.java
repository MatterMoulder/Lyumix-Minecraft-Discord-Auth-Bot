package com.matter_moulder.lyumixdiscordauth.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class Config {
    @ConfigSerializable
    public static class DatabaseConfig {
        @Comment("Database type (mongodb, postgresql, sqlite)")
        public String type = "sqlite";

        @Comment("Database connection string")
        public String connectionString = "";

        @Comment("Database username (for PostgreSQL)")
        public String username = "";

        @Comment("Database password (for PostgreSQL)")
        public String password = "";
    }

    @ConfigSerializable
    public static class DiscordConfig {
        @Comment("Discord bot token")
        public String botToken = "";

        @Comment("Discord server ID (leave empty to allow all servers)")
        public String discordServerId = "";

        @Comment("Allow users to unlink their accounts")
        public boolean allowUserUnlink = true;
    }

    @ConfigSerializable
    public static class LoginConfig {
        @Comment("Enable blindness effect while not authenticated")
        public boolean blindnessWhileLogin = true;

        @Comment("Auto-login time in hours (0 to disable)")
        public int autoLoginTime = 24;
    }

    @ConfigSerializable
    public static class TimerConfig {
        @Comment("Enable login timeout")
        public boolean enabled = true;

        @Comment("Login timeout in seconds")
        public int loginTime = 60;

        @Comment("Timer display title")
        public String title = "Time remaining: ";

        @Comment("First stage color (>secondTime)")
        public String firstColor = "GREEN";

        @Comment("Second stage color (thirdTime-secondTime)")
        public String secondColor = "YELLOW";

        @Comment("Final stage color (<thirdTime)")
        public String thirdColor = "RED";

        @Comment("Time in seconds for second stage")
        public int secondTime = 30;

        @Comment("Time in seconds for final stage")
        public int thirdTime = 15;
    }

    public DatabaseConfig database = new DatabaseConfig();
    public DiscordConfig discord = new DiscordConfig();
    public LoginConfig login = new LoginConfig();
    public TimerConfig loginTimer = new TimerConfig();
}