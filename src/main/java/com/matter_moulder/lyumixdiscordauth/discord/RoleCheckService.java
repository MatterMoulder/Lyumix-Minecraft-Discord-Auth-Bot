package com.matter_moulder.lyumixdiscordauth.discord;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;
import com.matter_moulder.lyumixdiscordauth.config.Config;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleCheckService {
    private RoleCheckService() {
    }

    public static CompletableFuture<Boolean> hasAccess(String discordUserId) {
        Config.DiscordConfig cfg = ConfigManager.conf().discord;
        if (!cfg.roleCheckEnabled) {
            return CompletableFuture.completedFuture(true);
        }

        List<String> requiredRoleIds = cfg.requiredRoleIds;
        if (requiredRoleIds == null || requiredRoleIds.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        Guild guild = resolveGuild(cfg.discordServerId);
        if (guild == null) {
            Server.getPluginLogger().warn("Role check enabled but guild is unavailable");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        guild.retrieveMemberById(discordUserId).queue(
                member -> result.complete(evaluateRoleAccess(cfg, requiredRoleIds, member)),
                error -> {
                    Server.getPluginLogger().warn("Failed to check Discord roles for user {}", Utils.sanitizeLog(discordUserId), error);
                    result.complete(false);
                }
        );

        // Fail closed if Discord does not respond quickly enough.
        return result.completeOnTimeout(false, 5, TimeUnit.SECONDS)
                .exceptionally(error -> false);
    }

    private static boolean evaluateRoleAccess(Config.DiscordConfig cfg, List<String> requiredRoleIds, Member member) {
        if (member == null) {
            return false;
        }

        Set<String> memberRoleIds = member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toSet());
        if (cfg.requireAllRoles) {
            return memberRoleIds.containsAll(requiredRoleIds);
        }
        return requiredRoleIds.stream().anyMatch(memberRoleIds::contains);
    }

    private static Guild resolveGuild(String guildId) {
        if (Server.getDsBot() == null || DiscordBotManager.getJda() == null) {
            return null;
        }

        if (guildId != null && !guildId.isBlank()) {
            return DiscordBotManager.getJda().getGuildById(guildId);
        }

        List<Guild> guilds = DiscordBotManager.getJda().getGuilds();
        if (guilds.isEmpty()) {
            return null;
        }
        return guilds.get(0);
    }
}

