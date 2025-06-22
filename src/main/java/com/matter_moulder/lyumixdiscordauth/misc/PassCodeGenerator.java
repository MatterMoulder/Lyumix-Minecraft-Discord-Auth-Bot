package com.matter_moulder.lyumixdiscordauth.misc;

public class PassCodeGenerator {
    public static String generatePassCode(int length, boolean includeLetters, boolean includeNumbers, boolean includeSpecialChars) {
        StringBuilder code = new StringBuilder();
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        if (!includeLetters && !includeNumbers && !includeSpecialChars) {
            throw new IllegalArgumentException("At least one character type must be included");
        }
        if (length > 32) {
            throw new IllegalArgumentException("Length must not exceed 32 characters");
        }
        if (length < 4) {
            throw new IllegalArgumentException("Length must be at least 4 characters to ensure diversity");
        }
        
        String characterSet = "";
        if (includeLetters) {
            characterSet += "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        }
        if (includeNumbers) {
            characterSet += "0123456789";
        }
        if (includeSpecialChars) {
            characterSet += "!@#$%^&*()-+";
        }
        for (int i = 0; i < length; i++) {
            int randomIndex = (int) (Math.random() * characterSet.length());
            code.append(characterSet.charAt(randomIndex));
        }
        return code.toString();
    }
}
