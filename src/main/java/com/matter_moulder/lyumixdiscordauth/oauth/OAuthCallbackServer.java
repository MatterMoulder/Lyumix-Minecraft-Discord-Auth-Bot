package com.matter_moulder.lyumixdiscordauth.oauth;

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.auth.AuthOrchestrator;
import com.matter_moulder.lyumixdiscordauth.auth.AuthStateManager;
import com.matter_moulder.lyumixdiscordauth.auth.PlayerAuthManager;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class OAuthCallbackServer {
    private static HttpServer server;
    private static ExecutorService executor;
    private static final AtomicInteger activeHandlers = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_HANDLERS = 20;
    private static final long TOKEN_BUCKET_CAPACITY = 10;
    private static final long TOKEN_BUCKET_REFILL_WINDOW_MILLIS = 60_000L;
    private static final long TOKEN_BUCKET_STALE_MILLIS = 5 * 60_000L;
    private static final long CALLBACK_DEADLINE_MILLIS = 15_000L;
    private static final int MAX_QUERY_LENGTH = 512;
    // long[0] = tokens remaining, long[1] = last refill millis.
    private static final ConcurrentHashMap<String, long[]> ipRateLimit = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ipLastSeen = new ConcurrentHashMap<>();
    private static final AtomicInteger cleanupCounter = new AtomicInteger(0);

    public static void start() throws IOException {
        if (server != null) {
            stop();
        }

        server = HttpServer.create(new InetSocketAddress(ConfigManager.conf().discord.oauthCallbackServerPort), 0);
        server.createContext("/callback", exchange -> {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method)) {
                writeResponse(exchange, 405, "<html><body>Method not allowed.</body></html>");
                return;
            }

            int current = activeHandlers.incrementAndGet();
            if (current > MAX_CONCURRENT_HANDLERS) {
                activeHandlers.decrementAndGet();
                writeResponse(exchange, 503, "<html><body>Server busy. Please retry.</body></html>");
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.length() > MAX_QUERY_LENGTH) {
                    writeResponse(exchange, 400, "<html><body>Invalid callback query.</body></html>");
                    return;
                }

                String remoteAddress = exchange.getRemoteAddress() == null
                        ? "unknown"
                        : exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!allowRequest(remoteAddress)) {
                    writeResponse(exchange, 429, "<html><body>Too many requests. Please retry later.</body></html>");
                    return;
                }

                long deadlineAt = System.currentTimeMillis() + CALLBACK_DEADLINE_MILLIS;
                Map<String, String> params = parseQuery(query);

                String code = params.get("code");
                String state = params.get("state");

                String html;
                int statusCode;

                if (code == null || state == null) {
                    html = "<html><body>Invalid OAuth callback. Missing code/state.</body></html>";
                    statusCode = 400;
                } else {
                    UUID playerUuid = AuthStateManager.getPlayerByState(state);
                    if (playerUuid == null) {
                        html = "<html><body>OAuth state expired or already used.</body></html>";
                        statusCode = 400;
                    } else {
                        try {
                            if (System.currentTimeMillis() > deadlineAt) {
                                failClosedPendingAuth(playerUuid);
                                writeResponse(exchange, 503, "<html><body>OAuth timed out. Please retry.</body></html>");
                                return;
                            }
                            String userId = DiscordOauthService.handleCallback(code);
                            if (System.currentTimeMillis() > deadlineAt) {
                                failClosedPendingAuth(playerUuid);
                                writeResponse(exchange, 503, "<html><body>OAuth timed out. Please retry.</body></html>");
                                return;
                            }
                            AuthOrchestrator.onOAuthCallback(playerUuid, userId);
                            html = "<html><body>Auth success! You can close this tab.</body></html>";
                            statusCode = 200;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            failClosedPendingAuth(playerUuid);
                            html = "<html><body>OAuth interrupted. Please return to the game and retry.</body></html>";
                            statusCode = 500;
                        } catch (Exception e) {
                            if (System.currentTimeMillis() > deadlineAt) {
                                failClosedPendingAuth(playerUuid);
                                html = "<html><body>OAuth timed out. Please retry.</body></html>";
                                statusCode = 503;
                            } else {
                                Server.getPluginLogger().error("OAuth callback failed", e);
                                failClosedPendingAuth(playerUuid);
                                html = "<html><body>OAuth failed. Please return to the game and retry.</body></html>";
                                statusCode = 500;
                            }
                        }
                    }
                }

                writeResponse(exchange, statusCode, html);
            } finally {
                activeHandlers.decrementAndGet();
            }
        });

        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        activeHandlers.set(0);
        ipRateLimit.clear();
        ipLastSeen.clear();
    }

    private static boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = ipRateLimit.computeIfAbsent(ip, ignored -> new long[]{TOKEN_BUCKET_CAPACITY, now});
        ipLastSeen.put(ip, now);

        synchronized (bucket) {
            long elapsed = now - bucket[1];
            if (elapsed > 0) {
                long refill = (elapsed * TOKEN_BUCKET_CAPACITY) / TOKEN_BUCKET_REFILL_WINDOW_MILLIS;
                if (refill > 0) {
                    bucket[0] = Math.min(TOKEN_BUCKET_CAPACITY, bucket[0] + refill);
                    bucket[1] = now;
                }
            }

            if (bucket[0] <= 0) {
                cleanupStaleRateLimitEntries(now);
                return false;
            }

            bucket[0] -= 1;
        }

        if ((cleanupCounter.incrementAndGet() & 63) == 0) {
            cleanupStaleRateLimitEntries(now);
        }
        return true;
    }

    private static void cleanupStaleRateLimitEntries(long now) {
        ipLastSeen.entrySet().removeIf(entry -> {
            boolean stale = (now - entry.getValue()) > TOKEN_BUCKET_STALE_MILLIS;
            if (stale) {
                ipRateLimit.remove(entry.getKey());
            }
            return stale;
        });
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.getResponseBody().close();
    }

    private static void failClosedPendingAuth(UUID playerUuid) {
        Server.getServer().execute(() -> {
            ServerPlayerEntity player = Server.getServer().getPlayerManager().getPlayer(playerUuid);
            if (player == null || !PlayerAuthManager.isPendingAuth(player)) {
                return;
            }
            player.sendMessage(net.minecraft.text.Text.literal(ConfigManager.msg().auth.authenticationFailed), false);
            PlayerAuthManager.kickPlayer(player);
        });
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(
                        URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }
}
