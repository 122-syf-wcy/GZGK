package com.gzly.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight security counters for the admin ops panel.
 *
 * <p>Only event names and hourly counters are stored. No credential, token, IP,
 * API key, or user input is written here.</p>
 */
@Slf4j
@Service
public class SecurityAuditCounterService {

    public static final String ADMIN_LOGIN_FAILURE = "admin_login_failure";
    public static final String CREDENTIAL_FAILURE = "credential_failure";
    public static final String RATE_LIMIT = "rate_limit";
    public static final String ADMIN_FORBIDDEN = "admin_forbidden";
    public static final String APP_5XX = "app_5xx";

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final int HOURS_24 = 24;
    private static final Duration KEY_TTL = Duration.ofHours(72);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    public void record(String event) {
        String safeEvent = safeEvent(event);
        String hour = hourKey(LocalDateTime.now());
        String key = key(safeEvent, hour);
        if (redisTemplate != null) {
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redisTemplate.expire(key, KEY_TTL);
                }
                return;
            } catch (Exception e) {
                log.debug("安全计数写入 Redis 失败，降级本地计数: event={}, error={}", safeEvent, e.getMessage());
            }
        }
        localCounters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long countLast24Hours(String event) {
        String safeEvent = safeEvent(event);
        long total = 0L;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < HOURS_24; i++) {
            String key = key(safeEvent, hourKey(now.minusHours(i)));
            total += readCounter(key);
        }
        return total;
    }

    private long readCounter(String key) {
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null && !value.isBlank()) {
                    return Long.parseLong(value);
                }
            } catch (Exception e) {
                log.debug("安全计数读取 Redis 失败，降级本地计数: error={}", e.getMessage());
            }
        }
        AtomicLong local = localCounters.get(key);
        return local == null ? 0L : local.get();
    }

    private String key(String event, String hour) {
        return "security:audit:" + event + ":" + hour;
    }

    private String hourKey(LocalDateTime time) {
        return time.format(HOUR_FORMAT);
    }

    private String safeEvent(String event) {
        return (event == null || event.isBlank() ? "unknown" : event)
                .replaceAll("[^A-Za-z0-9_.:-]", "_");
    }
}
