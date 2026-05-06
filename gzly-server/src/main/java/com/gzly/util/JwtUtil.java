package com.gzly.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${gzly.jwt.secret}")
    private String secret;

    @Value("${gzly.jwt.expire-hours}")
    private int expireHours;

    @Value("${gzly.jwt.admin-expire-hours:8}")
    private int adminExpireHours;

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
