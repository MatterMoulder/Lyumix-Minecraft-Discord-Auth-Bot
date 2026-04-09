package com.matter_moulder.lyumixdiscordauth.auth.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Handles client-side persistence and validation of refresh tokens.
 */
public final class ClientRefreshTokenStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRefreshTokenStore.class);
    private static final Gson GSON = new Gson();
    private static final long MAX_TOKEN_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final String TOKEN_KEY = "token";
    private static final String EXPIRES_AT_KEY = "expires_at";
    private static final String BINDING_HASH_KEY = "binding_hash";
    private static final String NONCE_KEY = "nonce";
    private static final String SINGLEPLAYER_ADDRESS = "singleplayer";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String PROPERTY_FALLBACK = "unknown";
    private static final String BINDING_SEPARATOR = "|";
    private static final Path STORE_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("lyumix-discord-auth")
            .resolve("client-refresh-token.json");

    private ClientRefreshTokenStore() {
    }

    public record ProbeBindingData(String bindingHash, String machineIdHash, String serverAddress) {
    }

    /**
     * Loads a valid locally cached refresh token, or an empty string when unavailable.
     */
    public static synchronized String loadToken() {
        try {
            if (!Files.exists(STORE_FILE)) {
                return "";
            }

            try (Reader reader = Files.newBufferedReader(STORE_FILE, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null || !json.has(TOKEN_KEY) || !json.has(EXPIRES_AT_KEY) || !json.has(BINDING_HASH_KEY)) {
                    return "";
                }

                long expiresAt = json.get(EXPIRES_AT_KEY).getAsLong();
                if (System.currentTimeMillis() > expiresAt) {
                    return "";
                }

                String storedNonce = json.has(NONCE_KEY) ? json.get(NONCE_KEY).getAsString() : "";
                String expectedBinding = computeBindingHash(computeMachineIdHash(), resolveServerAddressForBinding(), storedNonce);
                String storedBinding = json.get(BINDING_HASH_KEY).getAsString();
                if (!expectedBinding.equals(storedBinding)) {
                    return "";
                }

                return json.get(TOKEN_KEY).getAsString();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load cached refresh token", e);
            return "";
        }
    }

    /**
     * Stores refresh token metadata in the local client config directory.
     */
    public static synchronized void saveToken(String token) {
        try {
            Files.createDirectories(STORE_FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty(TOKEN_KEY, token);
            json.addProperty(EXPIRES_AT_KEY, System.currentTimeMillis() + MAX_TOKEN_TTL_MILLIS);
            String storedNonce = "";
            String machineIdHash = computeMachineIdHash();
            String serverAddress = resolveServerAddressForBinding();
            json.addProperty(NONCE_KEY, storedNonce);
            json.addProperty(BINDING_HASH_KEY, computeBindingHash(machineIdHash, serverAddress, storedNonce));


            try (Writer writer = Files.newBufferedWriter(STORE_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to save cached refresh token", e);
        }
    }

    public static synchronized ProbeBindingData buildProbeBinding(String serverNonce) {
        String normalizedNonce = serverNonce == null ? "" : serverNonce;
        String machineIdHash = computeMachineIdHash();
        String serverAddress = resolveServerAddressForBinding();
        String bindingHash = computeBindingHash(machineIdHash, serverAddress, normalizedNonce);
        return new ProbeBindingData(bindingHash, machineIdHash, serverAddress);
    }

    private static String computeMachineIdHash() {
        try {
            String machineId = System.getProperty("os.name", PROPERTY_FALLBACK)
                    + BINDING_SEPARATOR + System.getProperty("os.arch", PROPERTY_FALLBACK)
                    + BINDING_SEPARATOR + System.getProperty("user.name", PROPERTY_FALLBACK)
                    + BINDING_SEPARATOR + System.getProperty("user.home", PROPERTY_FALLBACK);

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(machineId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveServerAddressForBinding() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            return client.getCurrentServerEntry().address;
        }
        return SINGLEPLAYER_ADDRESS;
    }

    private static String computeBindingHash(String machineIdHash, String serverAddress, String serverNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String bindingInput = machineIdHash + BINDING_SEPARATOR + serverAddress + BINDING_SEPARATOR + serverNonce;
            byte[] hash = digest.digest(bindingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}

