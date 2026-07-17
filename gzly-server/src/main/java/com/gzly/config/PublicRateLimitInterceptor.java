package com.gzly.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.gzly.service.SecurityAuditCounterService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PublicRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final List<RateLimitRule> rules;
    private final SecurityAuditCounterService securityAuditCounterService;
    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    public PublicRateLimitInterceptor(StringRedisTemplate redisTemplate, List<RateLimitRule> rules) {
        this(redisTemplate, rules, null);
    }

    public PublicRateLimitInterceptor(StringRedisTemplate redisTemplate,
                                      List<RateLimitRule> rules,
                                      SecurityAuditCounterService securityAuditCounterService) {
        this.redisTemplate = redisTemplate;
        this.rules = rules;
        this.securityAuditCounterService = securityAuditCounterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        for (RateLimitRule rule : rules) {
            if (!rule.matches(path, request.getMethod())) {
                continue;
            }
            String clientIp = getClientIp(request);
            String key = "ratelimit:" + rule.key() + ":" + clientIp;
            if (redisTemplate == null) {
                if (!allowByLocalBucket(key, rule)) {
                    recordRateLimit();
                    reject(response, rule.windowSeconds());
                    return false;
                }
                continue;
            }
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redisTemplate.expire(key, Duration.ofSeconds(rule.windowSeconds()));
                }
                if (count != null && count > rule.limit()) {
                    recordRateLimit();
                    reject(response, rule.windowSeconds());
                    return false;
                }
            } catch (Exception e) {
                log.warn("Redis限流检查失败，启用本地降级限流: key={}, error={}", key, e.toString());
                if (!allowByLocalBucket(key, rule)) {
                    recordRateLimit();
                    reject(response, rule.windowSeconds());
                    return false;
                }
            }
        }
        return true;
    }

    private void recordRateLimit() {
        if (securityAuditCounterService != null) {
            securityAuditCounterService.record(SecurityAuditCounterService.RATE_LIMIT);
        }
    }

    private void reject(HttpServletResponse response, int retrySeconds) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retrySeconds));
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
    }

    private boolean allowByLocalBucket(String key, RateLimitRule rule) {
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, rule.windowSeconds()) * 1000L;
        LocalBucket bucket = localBuckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMs <= now) {
                return new LocalBucket(now + windowMs, new AtomicInteger(0));
            }
            return existing;
        });
        cleanupExpiredLocalBuckets(now);
        return bucket.count.incrementAndGet() <= rule.limit();
    }

    private void cleanupExpiredLocalBuckets(long now) {
        if (localBuckets.size() < 4096) {
            return;
        }
        localBuckets.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= now);
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip == null || ip.isBlank() ? "unknown" : ip;
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

    private record LocalBucket(long expiresAtMs, AtomicInteger count) {}

    public record RateLimitRule(String key, String path, String method, int limit, int windowSeconds) {
        public boolean matches(String requestPath, String requestMethod) {
            if (!method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            if (path.endsWith("/**")) {
                String prefix = path.substring(0, path.length() - 3);
                return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
            }
            if (path.contains("*")) {
                StringBuilder regex = new StringBuilder("^");
                for (int i = 0; i < path.length(); i++) {
                    char ch = path.charAt(i);
                    if (ch == '*') {
                        regex.append("[^/]+");
                    } else {
                        if ("\\.[]{}()+-^$?|".indexOf(ch) >= 0) {
                            regex.append('\\');
                        }
                        regex.append(ch);
                    }
                }
                regex.append('$');
                return requestPath.matches(regex.toString());
            }
            return path.equals(requestPath);
        }
    }
}
