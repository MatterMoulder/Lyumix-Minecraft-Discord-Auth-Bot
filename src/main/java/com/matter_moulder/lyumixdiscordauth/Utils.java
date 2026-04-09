package com.matter_moulder.lyumixdiscordauth;

import java.security.SecureRandom;

/**
 * Shared utility helpers used by both client and server flows.
 */
public class Utils {
    private static final String ALPHANUMERIC_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Generates a random alphanumeric code of the requested length.
     */
    public static String generateRandomCode(Integer length) {
        SecureRandom random = new SecureRandom();

        StringBuilder randomCodeBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(ALPHANUMERIC_CHARACTERS.length());
            randomCodeBuilder.append(ALPHANUMERIC_CHARACTERS.charAt(randomIndex));
        }

        return randomCodeBuilder.toString();
    }

    /**
     * Sanitizes user-provided text before logging to avoid multiline log injection.
     */
    public static String sanitizeLog(String input) {
        if (input == null) {
            return "<null>";
        }
        return input.replaceAll("[\\r\\n\\t]", "_");
    }
}
