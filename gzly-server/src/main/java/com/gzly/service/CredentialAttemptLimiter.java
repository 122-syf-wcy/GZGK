package com.gzly.service;

import com.gzly.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class CredentialAttemptLimiter {

    public static final int DEFAULT_LIMIT = 5;
    public static final int DEFAULT_WINDOW_SECONDS = 600;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    @Autowired(required = false)
    private SecurityAuditCounterService securityAuditCounterService;

    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    public String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = remoteAddr;
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    public void ensureNotLocked(String namespace, String clientIp, String target) {
        String key = key(namespace, clientIp, target);
        if (redisTemplate == null) {
            if (localCount(key) >= DEFAULT_LIMIT) {
                throw locked();
            }
            return;
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && Integer.parseInt(value) >= DEFAULT_LIMIT) {
                throw locked();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("凭证失败限流检查降级至本地计数: namespace={}, error={}", namespace, e.toString());
            if (localCount(key) >= DEFAULT_LIMIT) {
                throw locked();
            }
        }
    }

    public void recordFailure(String namespace, String clientIp, String target) {
        if (securityAuditCounterService != null) {
            securityAuditCounterService.record(SecurityAuditCounterService.CREDENTIAL_FAILURE);
        }
        String key = key(namespace, clientIp, target);
        if (redisTemplate == null) {
            localIncrement(key);
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(DEFAULT_WINDOW_SECONDS));
            }
            if (count != null && count >= DEFAULT_LIMIT) {
                log.warn("凭证失败次数达到阈值: namespace={}, ip={}, target={}",
                        namespace, maskIp(clientIp), hashPart(target));
            }
        } catch (Exception e) {
            log.warn("凭证失败计数降级至本地计数: namespace={}, error={}", namespace, e.toString());
            localIncrement(key);
        }
    }

    public void reset(String namespace, String clientIp, String target) {
        String key = key(namespace, clientIp, target);
        localBuckets.remove(key);
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("凭证失败计数清理失败: namespace={}, error={}", namespace, e.getMessage());
        }
    }

    private BizException locked() {
        return new BizException(429, "凭证尝试次数过多，请稍后再试");
    }

    private String key(String namespace, String clientIp, String target) {
        return "security:credential-fail:"
                + safe(namespace) + ":"
                + hashPart(clientIp == null ? "unknown" : clientIp) + ":"
                + hashPart(target == null || target.isBlank() ? "*" : target);
    }

    private String safe(String value) {
        return (value == null ? "unknown" : value).replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private int localCount(String key) {
        long now = System.currentTimeMillis();
        LocalBucket bucket = localBuckets.get(key);
        if (bucket == null || bucket.expiresAtMs <= now) {
            return 0;
        }
        return bucket.count.get();
    }

    private void localIncrement(String key) {
        long now = System.currentTimeMillis();
        long windowMs = DEFAULT_WINDOW_SECONDS * 1000L;
        localBuckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMs <= now) {
                return new LocalBucket(now + windowMs, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (localBuckets.size() >= 4096) {
            localBuckets.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= now);
        }
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.")
                || remoteAddr.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }

    private String hashPart(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
        }
        int keep = Math.min(6, ip.length());
        return ip.substring(0, keep) + "***";
    }

    private record LocalBucket(long expiresAtMs, AtomicInteger count) {}
}
