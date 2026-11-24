package com.gzgk.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Atomic Redis Lua fixed/sliding-window rate limiter with seamless Caffeine local-bucket degradation
 * when Redis is unavailable (prevents Redis outages from taking down the WebFlux gateway).
 */
@Component
public class SlidingWindowRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimitFilter.class);

    private static final RedisScript<Long> ATOMIC_INCR_EXPIRE = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]); " +
            "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
            "return c;",
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int readLimit;
    private final int recommendLimit;
    private final int agentLimit;

    private final Cache<String, AtomicInteger> localFallbackBuckets = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public SlidingWindowRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${gzgk.gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${gzgk.gateway.rate-limit.read-limit-per-min:600}") int readLimit,
            @Value("${gzgk.gateway.rate-limit.recommend-limit-per-min:60}") int recommendLimit,
            @Value("${gzgk.gateway.rate-limit.agent-limit-per-min:20}") int agentLimit
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.readLimit = readLimit;
        this.recommendLimit = recommendLimit;
        this.agentLimit = agentLimit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        String tier;
        int limit;
        if (path.startsWith("/api/v2/agent")) {
            tier = "agent";
            limit = agentLimit;
        } else if (path.contains("/recommend")) {
            tier = "recommend";
            limit = recommendLimit;
        } else {
            tier = "read";
            limit = readLimit;
        }

        String clientIp = resolveClientIp(exchange);
        String bucketKey = "gzgk:rl:" + tier + ":" + clientIp;

        return redisTemplate.execute(ATOMIC_INCR_EXPIRE, List.of(bucketKey), List.of("60000"))
                .next()
                .timeout(Duration.ofMillis(200))
                .onErrorResume(ex -> {
                    log.debug("Redis rate-limit degraded to local Caffeine bucket: {}", ex.getMessage());
                    AtomicInteger counter = localFallbackBuckets.get(bucketKey, k -> new AtomicInteger(0));
                    return Mono.just((long) counter.incrementAndGet());
                })
                .flatMap(count -> {
                    long remaining = Math.max(0, limit - count);
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));
                    if (count > limit) {
                        return rejectTooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> rejectTooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "60");
        byte[] body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
