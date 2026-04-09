package com.matter_moulder.lyumixdiscordauth.oauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.matter_moulder.lyumixdiscordauth.config.ConfigManager;
import com.matter_moulder.lyumixdiscordauth.config.SecretService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DiscordOauthService {
    private static final String AUTH_URL = "https://discord.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String USER_URL = "https://discord.com/api/users/@me";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── URL gen ─────────────────────────────────────────────

    public static String buildAuthUrl(String state) {
        var cfg = ConfigManager.conf().discord;
        String clientId = SecretService.getClientId();
        return AUTH_URL
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&scope=identify"
                + "&redirect_uri=" + URLEncoder.encode(cfg.discordOAuthRedirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    // ── Token exchange ────────────────────────────────────────────

    public static String exchangeCode(String code) throws IOException, InterruptedException {
        var cfg = ConfigManager.conf().discord;
        String clientId = SecretService.getClientId();
        String clientSecret = SecretService.getClientSecret();

        String body = "client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&grant_type=authorization_code"
                + "&code=" + code
                + "&redirect_uri=" + URLEncoder.encode(cfg.discordOAuthRedirectUri, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Discord token exchange failed (I/O/timeout)", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Discord token exchange failed: " + response.statusCode() + " " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    // ── User info ─────────────────────────────────────────────────

    public static String fetchUserId(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Discord user fetch failed (I/O/timeout)", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Discord user fetch failed: " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("id").getAsString();
    }

    // ── Full callback flow ────────────────────────────────────────

    public static String handleCallback(String code) throws IOException, InterruptedException {
        String accessToken = exchangeCode(code);
        return fetchUserId(accessToken);
        // accessToken rubbish to keep around, and we don't need to refresh it since we only use it once, so we discard it after fetching the user ID
    }
}
