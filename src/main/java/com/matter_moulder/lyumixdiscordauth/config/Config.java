package com.matter_moulder.lyumixdiscordauth.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.ArrayList;
import java.util.List;

@ConfigSerializable
public class Config {
    @Comment("Config version. Do not change this manually.")
    public int version = 1;
    
    @ConfigSerializable
    public static class DatabaseConfig {
        @Comment("Database type is fixed to sqlite for this mod.")
        public String type = "sqlite";
    }

    @ConfigSerializable
    public static class DiscordConfig {
        @Comment("Discord bot token. Get it from https://discord.com/developers/applications")
        public String botToken = "";

        @Comment("Discord server (guild) ID. Required when useDiscordOAuth is true.\nLeave empty to allow users from any server.")
        public String discordServerId = "";

        @Comment("Allow players to unlink their Discord account via Discord bot command.")
        public boolean allowUserUnlink = true;

        @Comment("Enable Discord OAuth2 authentication.\nWhen enabled, players must authorize via Discord on first join.\nRequires discordClientId, discordClientSecret and discordOAuthRedirectUri to be set.")
        public boolean useDiscordOAuth = false;

        @Comment("Authentication flow mode: oauth_notify_fallback, oauth_only, notify_only, or legacy (uses useDiscordOAuth).")
        public String authFlowMode = "legacy";

        @Comment("Local port for the OAuth2 callback HTTP server.\nMake sure this port is accessible via your reverse proxy or Cloudflare Tunnel.")
        public int oauthCallbackServerPort = 8080;

        @Comment("Redirect URI registered in your Discord application.\nMust exactly match the URI in Discord Developer Portal.\nExample for Cloudflare Tunnel: https://your-tunnel.trycloudflare.com/callback")
        public String discordOAuthRedirectUri = "";

        @Comment("Discord application client ID.\nGet it from https://discord.com/developers/applications → Your App → OAuth2")
        public String discordClientId = "";

        @Comment("Discord application client secret. Keep this value private, never share it.\nGet it from https://discord.com/developers/applications → Your App → OAuth2")
        public String discordClientSecret = "";

        @Comment("Enable role check before finishing authentication.")
        public boolean roleCheckEnabled = false;

        @Comment("Required Discord role IDs for login. Empty list disables role matching.")
        public List<String> requiredRoleIds = new ArrayList<>();

        @Comment("Require the player's Discord account to be a member of the configured Discord server (guild). " +
                "If true and the player is not in the guild, access is denied regardless of roles. " +
                "Only applies when discordServerId is set.")
        public boolean requireGuildMembership = true;

        @Comment("When true, user must have ALL required roles. When false, any one role is enough.")
        public boolean requireAllRoles = false;

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