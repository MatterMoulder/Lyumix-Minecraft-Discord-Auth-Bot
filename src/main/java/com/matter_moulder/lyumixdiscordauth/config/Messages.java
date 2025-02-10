package com.matter_moulder.lyumixdiscordauth.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class Messages {
    @ConfigSerializable
    public static class AuthMessages {
        @Comment("Message shown when login is required")
        public String loginRequired = "Authentication required. Please check Discord for verification.";

        @Comment("Message shown when player is not registered")
        public String notRegistered = "Your account is not registered. Please join our Discord server to register.";

        @Comment("Message shown when player is already registered")
        public String alreadyRegistered = "This account is already registered.";

        @Comment("Message shown when registration is successful")
        public String registrationSuccess = "Successfully registered! You can now play on the server.";

        @Comment("Message shown when player didn't authenticate")
        public String authenticationFailed = "Authentication failed. Please try again.";

        @Comment("Message shown when player didn't authenticate in time")
        public String authenticationTimeout = "Authentication timed out. Please reconnect and try again.";

        @Comment("Message shown when login request is rejected")
        public String loginRequestRejected = "Your login request was rejected. Please try again.";

        @Comment("Message shown when player joins the game")
        public String joinMessage = "%s joined the game";
    }

    @ConfigSerializable
    public static class DiscordMessages {
        @Comment("Message shown in Discord when account is unlinked")
        public String accountUnlinked = "Successfully unlinked Minecraft account: %s";

        @Comment("Instructions for server registration")
        public String serverIpMessage = "To register, join our Discord server and use the /register command.";

        @Comment("Message shown in Discord when unlinking is not allowed")
        public String unlinkNotAllowed = "Account unlinking is not allowed on this server.";

        @Comment("Message shown when wrong Discord server is used")
        public String wrongServer = "Wrong Discord server";

        @Comment("Message shown in Discord for login request")
        public String discordLoginRequest = "Login request from %s (IP: %s)";

        @Comment("Text for login approval button")
        public String loginApproved = "Approve";

        @Comment("Text for login rejection button")
        public String loginRejected = "Reject";

        @Comment("Message shown when login is approved")
        public String loginApprovedMessage = "Login approved";

        @Comment("Message shown when login is rejected")
        public String loginRejectedMessage = "Login rejected";

        @Comment("Message shown for unknown button interaction")
        public String unknownButton = "Unknown button";

        @Comment("Message shown when account link status is checked")
        public String accountLinkStatus = "Account link status for %s: %s";

        @Comment("Message shown when player tries to unlink account while online")
        public String unlinkWhileOnline = "You cannot unlink your account while you are online.";
    }


    @ConfigSerializable
    public static class AdminMessages {
        @Comment("Message shown when configuration is reloaded")
        public String configReloaded = "Configuration reloaded successfully";

        @Comment("Message shown when configuration fails to reload")
        public String configReloadFailed = "Failed to reload configuration";

        @Comment("Message shown when no linked account found for player")
        public String noLinkedAccountPlayer = "No linked account found for player: %s";

        @Comment("Message shown when no linked account found for Discord ID")
        public String noLinkedAccountDiscord = "No linked account found for Discord ID: %s";

        @Comment("Message shown when Discord ID is already linked")
        public String discordAlreadyLinked = "This Discord account is already linked to another player";

        @Comment("Message shown when force-link fails")
        public String forceLinkFailed = "Failed to force-link Discord account to player: %s";

        @Comment("Message shown when force-link succeeds")
        public String forceLinkSuccess = "Successfully force-linked Discord account to player: %s";
    }

    public AuthMessages auth = new AuthMessages();
    public DiscordMessages discord = new DiscordMessages();
    public AdminMessages admin = new AdminMessages();
} 