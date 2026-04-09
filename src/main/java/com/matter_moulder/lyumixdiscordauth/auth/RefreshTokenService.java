package com.matter_moulder.lyumixdiscordauth.auth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;
import com.matter_moulder.lyumixdiscordauth.db.DatabaseManager;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RefreshTokenService {
    private static final Gson GSON = new Gson();
    private static final Type STORE_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();

    private static final Path STORE_FILE = Server.getModFolder().resolve("refresh-tokens.json");
    private static final long REFRESH_TOKEN_TTL_MILLIS = TimeUnit.DAYS.toMillis(7);
    private static final String BINDING_SEPARATOR = "|";

    private static boolean migrationChecked;

    private RefreshTokenService() {
    }

    public static boolean validateAndRevokeOnReuse(
            String playerName,
            UUID playerUuid,
            String token,
            String clientBindingHash,
            String clientMachineIdHash,
            String clientServerAddress) {
        migrateLegacyStoreIfNeeded();
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            return false;
        }

        String nonce = AuthStateManager.getPendingNonce(playerUuid);
        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        String expectedBindingHash = computeBindingHash(clientMachineIdHash, clientServerAddress, nonce);
        if (!expectedBindingHash.equals(clientBindingHash)) {
            Server.getPluginLogger().warn("Refresh token binding mismatch for player {}.", Utils.sanitizeLog(playerUuid.toString()));
            return false;
        }

        Object playerId = db.getPlayerIdByName(playerName);
        if (playerId == null) {
            return false;
        }

        String expectedHash = db.getPlayerRefreshTokenHash(playerId);
        if (expectedHash == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long expiresAt = db.getPlayerRefreshTokenExpiresAt(playerId);
        if (expiresAt == null || expiresAt < now) {
            // Expired refresh tokens are treated as unauthenticated and must restart OAuth flow.
            db.clearPlayerRefreshTokenHash(playerId);
            return false;
        }

        String providedHash = hash(token);
        if (expectedHash.equals(providedHash) && db.consumeRefreshTokenIfValid(playerId, expectedHash, now)) {
            // Token is consumed immediately so concurrent replays fail closed.
            AuthStateManager.removePendingNonce(playerUuid);
            return true;
        }

        db.clearPlayerRefreshTokenHash(playerId);
        Server.getPluginLogger().warn("Refresh token reuse detected for player {}. Revoked all refresh keys.", Utils.sanitizeLog(playerName));
        return false;
    }

    public static synchronized String issueAndStore(String playerName) {
        migrateLegacyStoreIfNeeded();
        DatabaseManager db = Server.getDatabase();
        if (db == null) {
            return "";
        }

        Object playerId = db.getPlayerIdByName(playerName);
        if (playerId == null) {
            return "";
        }

        String token = generateToken();
        if (!db.setRefreshToken(playerId, hash(token), System.currentTimeMillis() + REFRESH_TOKEN_TTL_MILLIS)) {
            return "";
        }
        return token;
    }

    public static synchronized void migrateLegacyStoreIfNeeded() {
        if (migrationChecked) {
            return;
        }
        migrationChecked = true;

        try {
            Files.createDirectories(Server.getModFolder());
            if (!Files.exists(STORE_FILE)) {
                return;
            }

            try (Reader reader = Files.newBufferedReader(STORE_FILE, StandardCharsets.UTF_8)) {
                Map<String, String> data = GSON.fromJson(reader, STORE_TYPE);
                DatabaseManager db = Server.getDatabase();
                if (db != null && data != null) {
                    for (Map.Entry<String, String> entry : data.entrySet()) {
                        Object playerId = db.getPlayerIdByName(entry.getKey());
                        if (playerId != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                            db.setPlayerRefreshTokenHash(playerId, entry.getValue());
                            db.setPlayerRefreshTokenExpiresAt(playerId, System.currentTimeMillis() + REFRESH_TOKEN_TTL_MILLIS);
                        }
                    }
                }
            }
            Files.deleteIfExists(STORE_FILE);
        } catch (Exception e) {
            Server.getPluginLogger().error("Failed to migrate refresh token storage", e);
        }
    }

    private static String generateToken() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return B64_URL.encodeToString(random);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return B64_URL.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String computeBindingHash(String machineIdHash, String serverAddress, String serverNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String machine = machineIdHash == null ? "" : machineIdHash;
            String address = serverAddress == null ? "" : serverAddress;
            String nonce = serverNonce == null ? "" : serverNonce;
            String input = machine + BINDING_SEPARATOR + address + BINDING_SEPARATOR + nonce;
            return Base64.getEncoder().encodeToString(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

