package com.gzly.service;

import com.gzly.service.SiteStatsService.OnlineStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SiteStatsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private ZSetOperations zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SiteStatsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new SiteStatsService(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private String dailyKey() {
        return SiteStatsService.VISITS_DAILY_KEY_PREFIX + LocalDate.now(SiteStatsService.STATS_ZONE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_addsVisitorTrimsWindowAndReturnsCount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("User-Agent", "Mozilla/5.0 GZLYTest");

        when(zSetOperations.add(eq(SiteStatsService.ACTIVE_KEY), anyString(), anyDouble())).thenReturn(true);
        when(zSetOperations.removeRangeByScore(eq(SiteStatsService.ACTIVE_KEY), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(SiteStatsService.ACTIVE_KEY)).thenReturn(42L);
        when(redisTemplate.expire(eq(SiteStatsService.ACTIVE_KEY), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(SiteStatsService.VISITS_TOTAL_KEY)).thenReturn(123L);
        when(valueOperations.increment(dailyKey())).thenReturn(7L);

        OnlineStats stats = service.touchAndCount(request);

        assertThat(stats.getActiveUsers()).isEqualTo(42L);
        assertThat(stats.getWindowSeconds()).isEqualTo(SiteStatsService.ACTIVE_WINDOW_MS / 1000L);
        assertThat(stats.getTotalViews()).isEqualTo(123L);
        assertThat(stats.getTodayViews()).isEqualTo(7L);

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOperations).add(eq(SiteStatsService.ACTIVE_KEY), memberCaptor.capture(), anyDouble());
        assertThat(memberCaptor.getValue()).isNotBlank().hasSize(16);

        ArgumentCaptor<Double> minCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).removeRangeByScore(eq(SiteStatsService.ACTIVE_KEY), minCaptor.capture(), maxCaptor.capture());
        assertThat(minCaptor.getValue()).isEqualTo(0.0);
        assertThat(maxCaptor.getValue()).isLessThan((double) System.currentTimeMillis());

        verify(redisTemplate).expire(eq(SiteStatsService.ACTIVE_KEY), eq(SiteStatsService.ACTIVE_KEY_TTL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_fallsBackToLocalCounterWhenRedisFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("User-Agent", "FallbackUA");

        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("redis-down"));

        OnlineStats first = service.touchAndCount(request);
        assertThat(first.getActiveUsers()).isEqualTo(1L);
        assertThat(first.getTotalViews()).isEqualTo(1L);
        assertThat(first.getTodayViews()).isEqualTo(1L);

        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setRemoteAddr("198.51.100.21");
        other.addHeader("User-Agent", "FallbackUA-Other");
        OnlineStats second = service.touchAndCount(other);
        assertThat(second.getActiveUsers()).isEqualTo(2L);
        assertThat(second.getTotalViews()).isEqualTo(2L);
        assertThat(second.getTodayViews()).isEqualTo(2L);

        verify(zSetOperations, never()).zCard(anyString());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void visitorFingerprint_isStableForSameVisitor() {
        MockHttpServletRequest a = new MockHttpServletRequest();
        a.setRemoteAddr("203.0.113.55");
        a.addHeader("User-Agent", "RepeatVisitor/1.0");
        MockHttpServletRequest b = new MockHttpServletRequest();
        b.setRemoteAddr("203.0.113.55");
        b.addHeader("User-Agent", "RepeatVisitor/1.0");

        long now = System.currentTimeMillis();
        String fa = service.visitorFingerprint(a, now);
        String fb = service.visitorFingerprint(b, now + 1000);

        assertThat(fa).isEqualTo(fb).hasSize(16);
    }

    @Test
    void visitorFingerprint_distinguishesByIp() {
        MockHttpServletRequest a = new MockHttpServletRequest();
        a.setRemoteAddr("203.0.113.55");
        a.addHeader("User-Agent", "DiffVisitorUA");
        MockHttpServletRequest b = new MockHttpServletRequest();
        b.setRemoteAddr("203.0.113.66");
        b.addHeader("User-Agent", "DiffVisitorUA");

        long now = System.currentTimeMillis();
        assertThat(service.visitorFingerprint(a, now)).isNotEqualTo(service.visitorFingerprint(b, now));
    }

    @Test
    void touchAndCount_ignoresTrustedProxyForwardedHeader_whenRemoteIsPublic() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.100");
        request.addHeader("X-Forwarded-For", "10.0.0.99");
        request.addHeader("User-Agent", "PublicProxyTest");

        MockHttpServletRequest behindProxy = new MockHttpServletRequest();
        behindProxy.setRemoteAddr("10.0.0.5");
        behindProxy.addHeader("X-Forwarded-For", "203.0.113.100");
        behindProxy.addHeader("User-Agent", "PublicProxyTest");

        long now = System.currentTimeMillis();
        String publicFingerprint = service.visitorFingerprint(request, now);
        String behindProxyFingerprint = service.visitorFingerprint(behindProxy, now);

        assertThat(behindProxyFingerprint).isEqualTo(publicFingerprint);
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_setsDailyKeyTtlOnFirstIncrementOnly() {
        MockHttpServletRequest visitorA = new MockHttpServletRequest();
        visitorA.setRemoteAddr("203.0.113.30");
        visitorA.addHeader("User-Agent", "DailyKeyTtlA");
        MockHttpServletRequest visitorB = new MockHttpServletRequest();
        visitorB.setRemoteAddr("203.0.113.31");
        visitorB.addHeader("User-Agent", "DailyKeyTtlB");

        when(zSetOperations.add(eq(SiteStatsService.ACTIVE_KEY), anyString(), anyDouble())).thenReturn(true);
        when(zSetOperations.zCard(SiteStatsService.ACTIVE_KEY)).thenReturn(1L, 2L);
        when(valueOperations.increment(SiteStatsService.VISITS_TOTAL_KEY)).thenReturn(1L, 2L);
        when(valueOperations.increment(dailyKey())).thenReturn(1L, 2L);

        service.touchAndCount(visitorA);
        service.touchAndCount(visitorB);

        verify(redisTemplate, times(1)).expire(eq(dailyKey()), eq(SiteStatsService.DAILY_KEY_TTL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_doesNotIncrementOnRepeatVisitorWithinWindow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.40");
        request.addHeader("User-Agent", "RepeatVisitorUA");

        when(zSetOperations.add(eq(SiteStatsService.ACTIVE_KEY), anyString(), anyDouble())).thenReturn(false);
        when(zSetOperations.zCard(SiteStatsService.ACTIVE_KEY)).thenReturn(5L);
        when(valueOperations.get(SiteStatsService.VISITS_TOTAL_KEY)).thenReturn("999");
        when(valueOperations.get(dailyKey())).thenReturn("88");

        OnlineStats stats = service.touchAndCount(request);

        assertThat(stats.getActiveUsers()).isEqualTo(5L);
        assertThat(stats.getTotalViews()).isEqualTo(999L);
        assertThat(stats.getTodayViews()).isEqualTo(88L);
        verify(valueOperations, never()).increment(anyString());
        verify(redisTemplate, never()).expire(eq(dailyKey()), any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_localCounterCountsRepeatVisitorOnceWithinWindow() {
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("redis-down"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.55");
        request.addHeader("User-Agent", "LocalRepeatUA");

        OnlineStats first = service.touchAndCount(request);
        OnlineStats again = service.touchAndCount(request);

        assertThat(first.getTotalViews()).isEqualTo(1L);
        assertThat(first.getTodayViews()).isEqualTo(1L);
        assertThat(again.getTotalViews()).isEqualTo(1L);
        assertThat(again.getTodayViews()).isEqualTo(1L);
        assertThat(again.getActiveUsers()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_handlesNonNumericCounterAsZeroFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("User-Agent", "NonNumericUA");

        when(zSetOperations.add(eq(SiteStatsService.ACTIVE_KEY), anyString(), anyDouble())).thenReturn(false);
        when(zSetOperations.zCard(SiteStatsService.ACTIVE_KEY)).thenReturn(3L);
        when(valueOperations.get(SiteStatsService.VISITS_TOTAL_KEY)).thenReturn("not-a-number");
        when(valueOperations.get(dailyKey())).thenReturn(null);

        OnlineStats stats = service.touchAndCount(request);

        assertThat(stats.getActiveUsers()).isEqualTo(3L);
        assertThat(stats.getTotalViews()).isEqualTo(0L);
        assertThat(stats.getTodayViews()).isEqualTo(0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchAndCount_localDailyCounterRollsOverByDate() {
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("redis-down"));

        MockHttpServletRequest day1Visitor = new MockHttpServletRequest();
        day1Visitor.setRemoteAddr("198.51.100.1");
        day1Visitor.addHeader("User-Agent", "DayOneUA");

        OnlineStats day1 = service.touchAndCount(day1Visitor);
        Map<String, Long> snapshot = new HashMap<>();
        snapshot.put("totalViews", day1.getTotalViews());
        snapshot.put("todayViews", day1.getTodayViews());

        assertThat(snapshot.get("totalViews")).isEqualTo(1L);
        assertThat(snapshot.get("todayViews")).isEqualTo(1L);
    }
}
