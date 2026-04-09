package com.matter_moulder.lyumixdiscordauth.discord;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;
import com.matter_moulder.lyumixdiscordauth.config.Config;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleCheckService {
    private RoleCheckService() {
    }

    public enum AccessResultType {
        OK,
        MISCONFIG,
        NO_GUILD,
        NOT_A_MEMBER,
        ROLE_MISSING,
        ERROR,
        TIMEOUT
    }

    public record AccessResult(AccessResultType type) {
        public boolean allowed() {
            return type == AccessResultType.OK;
        }
    }

    public static CompletableFuture<AccessResult> hasAccess(String discordUserId) {
        Config.DiscordConfig cfg = ConfigManager.conf().discord;
        List<String> requiredRoleIds = cfg.requiredRoleIds != null ? cfg.requiredRoleIds : Collections.emptyList();
        boolean hasRequiredRoles = !requiredRoleIds.isEmpty();
        boolean guildConfigured = cfg.discordServerId != null && !cfg.discordServerId.isBlank();

        if ((cfg.requireGuildMembership || cfg.roleCheckEnabled) && !guildConfigured) {
            return CompletableFuture.completedFuture(new AccessResult(AccessResultType.MISCONFIG));
        }

        if (!cfg.requireGuildMembership && !cfg.roleCheckEnabled) {
            return CompletableFuture.completedFuture(new AccessResult(AccessResultType.OK));
        }

        Guild guild = resolveGuild(cfg.discordServerId);
        if (guild == null) {
            Server.getPluginLogger().warn("Guild unavailable for user {}", Utils.sanitizeLog(discordUserId));
            return CompletableFuture.completedFuture(new AccessResult(AccessResultType.NO_GUILD));
        }

        if (cfg.requireGuildMembership) {
            Member cachedMember = guild.getMemberById(discordUserId);
            // Cache hit — skip API call.
            if (cachedMember != null) {
                return CompletableFuture.completedFuture(
                        evaluateAfterMembership(cfg, requiredRoleIds, hasRequiredRoles, cachedMember)
                );
            }
            // Cache miss — retrieve async and reuse member for role check.
            CompletableFuture<AccessResult> result = new CompletableFuture<>();
            guild.retrieveMemberById(discordUserId).queue(
                    member -> {
                        if (member == null) {
                            result.complete(new AccessResult(AccessResultType.NOT_A_MEMBER));
                            return;
                        }
                        result.complete(
                                evaluateAfterMembership(cfg, requiredRoleIds, hasRequiredRoles, member)
                        );
                    },
                    error -> {
                        // Unknown member is a normal denial, not an error.
                        if (!(error instanceof ErrorResponseException ere
                                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER)) {
                            Server.getPluginLogger().warn("Failed to check guild membership/roles for user {}",
                                    Utils.sanitizeLog(discordUserId), error);
                            result.complete(new AccessResult(AccessResultType.ERROR));
                        } else {
                            result.complete(new AccessResult(AccessResultType.NOT_A_MEMBER));
                        }
                    }
            );
            return result.completeOnTimeout(new AccessResult(AccessResultType.TIMEOUT), 5, TimeUnit.SECONDS)
                    .exceptionally(e -> new AccessResult(AccessResultType.ERROR));
        }

        if (!hasRequiredRoles) {
            return CompletableFuture.completedFuture(new AccessResult(AccessResultType.OK));
        }

        CompletableFuture<AccessResult> result = new CompletableFuture<>();
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    if (member == null) {
                        result.complete(new AccessResult(AccessResultType.NOT_A_MEMBER));
                        return;
                    }
                    result.complete(evaluateRoleAccess(cfg, requiredRoleIds, member));
                },
                error -> {
                    if (error instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                        result.complete(new AccessResult(AccessResultType.NOT_A_MEMBER));
                        return;
                    }
                    Server.getPluginLogger().warn("Failed to check Discord roles for user {}",
                            Utils.sanitizeLog(discordUserId), error);
                    result.complete(new AccessResult(AccessResultType.ERROR));
                }
        );
        return result.completeOnTimeout(new AccessResult(AccessResultType.TIMEOUT), 5, TimeUnit.SECONDS)
                .exceptionally(e -> new AccessResult(AccessResultType.ERROR));
    }

    private static AccessResult evaluateAfterMembership(Config.DiscordConfig cfg, List<String> requiredRoleIds, boolean hasRequiredRoles, Member member) {
        if (!cfg.roleCheckEnabled || !hasRequiredRoles) {
            return new AccessResult(AccessResultType.OK);
        }
        return evaluateRoleAccess(cfg, requiredRoleIds, member);
    }

    private static AccessResult evaluateRoleAccess(Config.DiscordConfig cfg, List<String> requiredRoleIds, Member member) {
        if (member == null) {
            return new AccessResult(AccessResultType.NOT_A_MEMBER);
        }

        Set<String> memberRoleIds = member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toSet());
        boolean ok = cfg.requireAllRoles
                ? memberRoleIds.containsAll(requiredRoleIds)
                : requiredRoleIds.stream().anyMatch(memberRoleIds::contains);

        return ok
                ? new AccessResult(AccessResultType.OK)
                : new AccessResult(AccessResultType.ROLE_MISSING);
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

