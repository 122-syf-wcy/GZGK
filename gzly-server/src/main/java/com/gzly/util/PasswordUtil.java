package com.gzly.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordUtil {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private PasswordUtil() {
    }

    public static String hash(String password) {
        return PASSWORD_ENCODER.encode(password == null ? "" : password);
    }

    public static boolean matches(String password, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String raw = password == null ? "" : password;
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return PASSWORD_ENCODER.matches(raw, storedHash);
        }
        return storedHash.equals(sha256(raw));
    }

    public static boolean needsRehash(String storedHash) {
        return storedHash == null || !(storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"));
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
