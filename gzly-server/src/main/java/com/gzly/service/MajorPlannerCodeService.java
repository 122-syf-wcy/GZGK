package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * 专业选择规划码服务。
 *
 * <p>完整规划码只在创建结果时返回一次；数据库只保存 BCrypt hash、确定性 fingerprint 和掩码。</p>
 */
@Service
public class MajorPlannerCodeService {

    private static final char[] CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int GENERATED_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${gzly.jwt.secret:}")
    private String jwtSecret;

    public String generateCode() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(CHARSET[RANDOM.nextInt(CHARSET.length)]);
        }
        return sb.toString();
    }

    public String normalize(String code) {
        if (code == null) {
            throw new BizException("请输入规划码");
        }
        String normalized = code.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BizException("请输入规划码");
        }
        if (normalized.length() < 8 || normalized.length() > 24) {
            throw new BizException("规划码长度需为8-24位");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) {
                throw new BizException("规划码仅支持字母和数字");
            }
        }
        return normalized;
    }

    public String hash(String code) {
        return BCrypt.hashpw(payload(normalize(code)), BCrypt.gensalt(10));
    }

    public String fingerprint(String code) {
        return sha256Hex("major-planner-code-fingerprint:" + normalize(code) + ":" + pepper());
    }

    public boolean verify(String code, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(payload(normalize(code)), storedHash.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public String mask(String code) {
        String normalized;
        try {
            normalized = normalize(code);
        } catch (Exception e) {
            return "****";
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
    }

    private String payload(String normalizedCode) {
        return normalizedCode + ":" + pepper();
    }

    private String pepper() {
        return sha256Hex("major-planner-code:" + (jwtSecret == null ? "" : jwtSecret.trim()));
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
            throw new BizException("规划码处理失败");
        }
    }
}
