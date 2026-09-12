package com.gzly.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /** HS256 的密钥最小字节数（256 bit）。 */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${gzly.jwt.secret}")
    private String secret;

    @Value("${gzly.jwt.expire-hours}")
    private int expireHours;

    @Value("${gzly.jwt.admin-expire-hours:8}")
    private int adminExpireHours;

    /**
     * 启动期校验 JWT 密钥：非空白且 UTF-8 字节数 ≥ 32。
     * 空或过短的密钥会让签名形同虚设，故 fail-fast 终止启动，避免带病上线。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "配置项 gzly.jwt.secret 未设置：JWT 密钥不能为空，请配置环境变量 GZLY_JWT_SECRET"
                            + "（至少 32 字节的随机串）。");
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "配置项 gzly.jwt.secret 过短：当前 " + length + " 字节，要求 UTF-8 编码下至少 "
                            + MIN_SECRET_BYTES + " 字节。请将环境变量 GZLY_JWT_SECRET 设置为至少 32 字节的随机串。");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String identifier) {
        return generate(userId, identifier, "user");
    }

    public String generate(Long userId, String identifier, String role) {
        return generate(userId, identifier, role, expireHours);
    }

    public String generateAdmin(Long userId, String identifier) {
        return generate(userId, identifier, "admin", adminExpireHours);
    }

    private String generate(Long userId, String identifier, String role, int tokenExpireHours) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("identifier", identifier)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (long) tokenExpireHours * 3600 * 1000))
                .signWith(getKey())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        try {
            Claims claims = parse(token);
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getRole(String token) {
        try {
            Claims claims = parse(token);
            Object role = claims.get("role");
            return role != null ? String.valueOf(role) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public String getIdentifier(String token) {
        try {
            Claims claims = parse(token);
            Object identifier = claims.get("identifier");
            return identifier != null ? String.valueOf(identifier) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
