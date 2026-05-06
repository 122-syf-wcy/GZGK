package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

@Service
public class SafetyCodeService {
    private static final char[] CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int GENERATED_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${gzly.jwt.secret}")
    private String jwtSecret;

    public SafetyCodeIssue issue(String requestedCode) {
        String code = normalize(requestedCode);
        if (code.isBlank()) {
            code = generate();
        }
        return new SafetyCodeIssue(code, hash(code));
    }

    public boolean verify(Long planId, String submittedCode, String storedHash) {
        String raw = safeTrim(submittedCode);
        if (planId == null || planId <= 0 || raw.isBlank()) {
            return false;
        }
        if (storedHash != null && !storedHash.isBlank()) {
            try {
                String code = normalize(raw);
                return BCrypt.checkpw(hashPayload(code), storedHash.trim());
            } catch (Exception e) {
                return false;
            }
        }
        return MessageDigest.isEqual(
                buildLegacyAccessKey(planId).getBytes(StandardCharsets.UTF_8),
                raw.getBytes(StandardCharsets.UTF_8));
    }

    public String buildLegacyAccessKey(Long planId) {
        if (planId == null || planId <= 0) {
            return "";
        }
        return sha256Hex("plan:" + planId + ":" + safeTrim(jwtSecret));
    }

    public String mask(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(CHARSET[RANDOM.nextInt(CHARSET.length)]);
        }
        return sb.toString();
    }

    private String normalize(String code) {
        if (code == null) {
            return "";
        }
        String normalized = code.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() < 6 || normalized.length() > 24) {
            throw new BizException("安全码长度需为6-24位");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) {
                throw new BizException("安全码仅支持字母和数字");
            }
        }
        return normalized;
    }

    private String hash(String code) {
        return BCrypt.hashpw(hashPayload(code), BCrypt.gensalt(10));
    }

    private String hashPayload(String code) {
        return normalize(code) + ":" + safeTrim(jwtSecret);
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("安全码处理失败");
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record SafetyCodeIssue(String safetyCode, String safetyCodeHash) {
    }
}
