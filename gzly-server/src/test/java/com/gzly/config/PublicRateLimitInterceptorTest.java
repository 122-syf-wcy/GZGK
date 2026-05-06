package com.gzly.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRateLimitInterceptorTest {

    @Test
    void shouldMatchExactPaths() {
        PublicRateLimitInterceptor.RateLimitRule rule =
                new PublicRateLimitInterceptor.RateLimitRule("login", "/api/admin/login", "POST", 5, 60);

        assertThat(rule.matches("/api/admin/login", "post")).isTrue();
        assertThat(rule.matches("/api/admin/login/extra", "POST")).isFalse();
    }

    @Test
    void shouldMatchWildcardPaths() {
        PublicRateLimitInterceptor.RateLimitRule rule =
                new PublicRateLimitInterceptor.RateLimitRule("qa-like", "/api/qa/like/**", "POST", 60, 600);

        assertThat(rule.matches("/api/qa/like/123", "POST")).isTrue();
        assertThat(rule.matches("/api/qa/like/123/extra", "POST")).isTrue();
        assertThat(rule.matches("/api/qa/ask", "POST")).isFalse();
    }

    @Test
    void shouldUseLocalLimiterWhenRedisUnavailable() throws Exception {
        PublicRateLimitInterceptor.RateLimitRule rule =
                new PublicRateLimitInterceptor.RateLimitRule("login", "/api/admin/login", "POST", 1, 60);
        PublicRateLimitInterceptor interceptor = new PublicRateLimitInterceptor(null, List.of(rule));

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/admin/login");
        first.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/admin/login");
        second.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(first, firstResponse, new Object())).isTrue();
        assertThat(interceptor.preHandle(second, secondResponse, new Object())).isFalse();
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldIgnoreSpoofedForwardedForFromUntrustedRemote() throws Exception {
        PublicRateLimitInterceptor.RateLimitRule rule =
                new PublicRateLimitInterceptor.RateLimitRule("login", "/api/admin/login", "POST", 1, 60);
        PublicRateLimitInterceptor interceptor = new PublicRateLimitInterceptor(null, List.of(rule));

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/admin/login");
        first.setRemoteAddr("203.0.113.9");
        first.addHeader("X-Forwarded-For", "198.51.100.1");

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/admin/login");
        second.setRemoteAddr("203.0.113.9");
        second.addHeader("X-Forwarded-For", "198.51.100.2");

        assertThat(interceptor.preHandle(first, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(second, new MockHttpServletResponse(), new Object())).isFalse();
    }
}
