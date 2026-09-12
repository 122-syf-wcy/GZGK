package com.gzly.service;

import com.gzly.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 维护一个滑动 5 分钟的活跃访客集合，用于首页"实时浏览量"展示，
 * 并在新指纹首次进入活跃窗口时累加"总浏览量 / 当日浏览量"。
 *
 * 主路径走 Redis ZSET：member 为访客指纹（IP + UA 截断哈希），score 为时间戳。
 * Redis 异常时降级到进程内本地集合 / AtomicLong，避免拖累首页。
 *
 * 计数语义：30 秒轮询不会污染累计值；同一访客 5 分钟内连续访问只算 1 次浏览，
 * 离开 5 分钟后重新出现算新一次。所有数据仅做近似展示，不用于鉴权或合规审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteStatsService {

    static final String ACTIVE_KEY = "gzly:online:active";
    static final String VISITS_TOTAL_KEY = "gzly:visits:total";
    static final String VISITS_DAILY_KEY_PREFIX = "gzly:visits:daily:";
    static final long ACTIVE_WINDOW_MS = 5 * 60 * 1000L;
    static final Duration ACTIVE_KEY_TTL = Duration.ofMinutes(30);
    static final Duration DAILY_KEY_TTL = Duration.ofDays(35);
    static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int LOCAL_BUCKET_LIMIT = 8192;
    private static final int LOCAL_DAILY_LIMIT = 35;

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Long> localBucket = new ConcurrentHashMap<>();
    private final AtomicLong localTotalViews = new AtomicLong();
    private final Map<LocalDate, AtomicLong> localDailyViews = new ConcurrentHashMap<>();

    public OnlineStats touchAndCount(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(STATS_ZONE);
        String dailyKey = VISITS_DAILY_KEY_PREFIX + today;
        String fingerprint = visitorFingerprint(request, now);

        long activeCount = -1L;
        long totalViews = -1L;
        long todayViews = -1L;
        boolean newVisitor = false;

        if (redisTemplate != null) {
            try {
                Boolean added = redisTemplate.opsForZSet().add(ACTIVE_KEY, fingerprint, now);
                redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_KEY, 0, now - ACTIVE_WINDOW_MS - 1);
                redisTemplate.expire(ACTIVE_KEY, ACTIVE_KEY_TTL);
                Long zcard = redisTemplate.opsForZSet().zCard(ACTIVE_KEY);
                if (zcard != null) {
                    activeCount = zcard;
                }
                newVisitor = Boolean.TRUE.equals(added);
                if (newVisitor) {
                    Long total = redisTemplate.opsForValue().increment(VISITS_TOTAL_KEY);
                    if (total != null) {
                        totalViews = total;
                    }
                    Long daily = redisTemplate.opsForValue().increment(dailyKey);
                    if (daily != null) {
                        todayViews = daily;
                        if (daily == 1L) {
                            redisTemplate.expire(dailyKey, DAILY_KEY_TTL);
                        }
                    }
                } else {
                    Long total = parseLong(redisTemplate.opsForValue().get(VISITS_TOTAL_KEY));
                    if (total != null) {
                        totalViews = total;
                    }
                    Long daily = parseLong(redisTemplate.opsForValue().get(dailyKey));
                    if (daily != null) {
                        todayViews = daily;
                    }
                }
            } catch (Exception e) {
                log.warn("SiteStats Redis 路径异常，降级到本地集合: {}", e.toString());
            }
        }

        if (activeCount < 0) {
            LocalCounts local = touchLocal(fingerprint, now, today);
            activeCount = local.activeCount;
            newVisitor = local.newVisitor;
        }
        if (totalViews < 0) {
            totalViews = newVisitor ? localTotalViews.incrementAndGet() : localTotalViews.get();
        }
        if (todayViews < 0) {
            AtomicLong dailyCounter = localDailyViews.computeIfAbsent(today, d -> new AtomicLong());
            todayViews = newVisitor ? dailyCounter.incrementAndGet() : dailyCounter.get();
            trimLocalDailyIfNeeded();
        }

        OnlineStats stats = new OnlineStats();
        stats.setActiveUsers(Math.max(activeCount, 0L));
        stats.setWindowSeconds(ACTIVE_WINDOW_MS / 1000L);
        stats.setTotalViews(Math.max(totalViews, 0L));
        stats.setTodayViews(Math.max(todayViews, 0L));
        return stats;
    }

    private LocalCounts touchLocal(String fingerprint, long now, LocalDate today) {
        long cutoff = now - ACTIVE_WINDOW_MS;
        Long previous = localBucket.put(fingerprint, now);
        boolean newVisitor = previous == null || previous < cutoff;
        if (localBucket.size() > LOCAL_BUCKET_LIMIT) {
            localBucket.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
        long active = localBucket.values().stream().filter(ts -> ts >= cutoff).count();
        return new LocalCounts(active, newVisitor);
    }

    private void trimLocalDailyIfNeeded() {
        if (localDailyViews.size() <= LOCAL_DAILY_LIMIT) {
            return;
        }
        LocalDate cutoff = LocalDate.now(STATS_ZONE).minusDays(LOCAL_DAILY_LIMIT);
        localDailyViews.keySet().removeIf(date -> date.isBefore(cutoff));
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("SiteStats counter 值不是合法数字: {}", raw);
            return null;
        }
    }

    String visitorFingerprint(HttpServletRequest request, long now) {
        String ip = clientIp(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        if (ua == null) {
            ua = "";
        }
        if (ua.length() > 200) {
            ua = ua.substring(0, 200);
        }
        String raw = ip + "|" + ua;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((raw + ":" + now).hashCode());
        }
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolveOrUnknown(request);
    }

    private record LocalCounts(long activeCount, boolean newVisitor) {
    }

    @Data
    public static class OnlineStats {
        private long activeUsers;
        private long windowSeconds;
        private long totalViews;
        private long todayViews;
    }
}
