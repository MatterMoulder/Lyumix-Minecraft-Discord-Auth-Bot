package com.matter_moulder.lyumixdiscordauth.db;

import com.matter_moulder.lyumixdiscordauth.Server;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecretCryptoService {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private SecretCryptoService() {
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(buildKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + B64.encodeToString(iv) + ":" + B64.encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        if (!encrypted.startsWith(PREFIX)) {
            return encrypted;
        }
        try {
            String payload = encrypted.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                return null;
            }

            byte[] iv = B64D.decode(parts[0]);
            byte[] cipherText = B64D.decode(parts[1]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(buildKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Server.getPluginLogger().warn("Failed to decrypt secret from DB");
            return null;
        }
    }

    private static byte[] buildKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] full = digest.digest(("secret|" + deriveKeyMaterial()).getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(full, 0, key, 0, key.length);
        return key;
    }

    private static String deriveKeyMaterial() {
        StringBuilder material = new StringBuilder("lyumix-secret-v1|");

        // Stable unique machine identifier to prevent key reuse across different servers, but allow moving the installation to a new location on the same machine without invalidating secrets.
        Path machineId = Path.of("/etc/machine-id");
        if (machineId.toFile().exists()) {
            try {
                material.append(Files.readString(machineId).trim());
            } catch (IOException ignored) {}
        } else {
            Server.getPluginLogger().warn(
                    "Cannot find /etc/machine-id — secret key derivation uses mod path only. " +
                            "Moving the mod folder will invalidate stored secrets."
            );
        }

        // Absolute path to mod folder to bind secrets to the installation and prevent cross-instance key reuse.
        material.append('|').append(Server.getModFolder().toAbsolutePath());

        return material.toString();
    }
}

