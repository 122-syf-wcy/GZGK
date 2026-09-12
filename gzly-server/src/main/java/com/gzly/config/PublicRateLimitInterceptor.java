package com.gzly.config;

import com.gzly.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
public class PublicRateLimitInterceptor implements HandlerInterceptor {

    private static final int LOCAL_BUCKET_LIMIT = 4096;

    /**
     * 原子计数脚本：INCR + 首次写入时设置 TTL，避免 increment 与 expire 两次独立往返之间
     * 进程中断/expire 丢失导致 key 永久无 TTL 滞留（历史上会造成 IP 级永久封禁）。
     * ARGV[1] 为过期时间（毫秒）。
     */
    private static final DefaultRedisScript<Long> INCR_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            "local c=redis.call('INCR',KEYS[1]) if c==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final List<RateLimitRule> rules;
    // 带上限的 LRU 桶：size 超过 LOCAL_BUCKET_LIMIT 时淘汰最久未访问的 key，
    // 不再依赖"size>=4096 才顺带清理已过期项"这种可能长期无法收敛的策略。
    private final Map<String, LocalBucket> localBuckets = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LocalBucket> eldest) {
                    return size() > LOCAL_BUCKET_LIMIT;
                }
            });

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
                    reject(response, rule.windowSeconds());
                    return false;
                }
                continue;
            }
            try {
                Long count = redisTemplate.execute(
                        INCR_WITH_TTL_SCRIPT,
                        List.of(key),
                        String.valueOf(Math.max(1, rule.windowSeconds()) * 1000L));
                if (count != null && count > rule.limit()) {
                    reject(response, rule.windowSeconds());
                    return false;
                }
            } catch (Exception e) {
                log.warn("Redis限流检查失败，启用本地降级限流: key={}, error={}", key, e.toString());
                if (!allowByLocalBucket(key, rule)) {
                    reject(response, rule.windowSeconds());
                    return false;
                }
            }
        }
        return true;
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
        return bucket.count.incrementAndGet() <= rule.limit();
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolveOrUnknown(request);
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
            return path.equals(requestPath);
        }
    }
}
