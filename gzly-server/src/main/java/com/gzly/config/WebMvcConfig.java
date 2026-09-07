package com.gzly.config;

import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${gzly.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    @Value("${gzly.rate-limit.volunteer-generate-limit:10}")
    private int volunteerGenerateLimit;
    @Value("${gzly.rate-limit.volunteer-generate-window-seconds:60}")
    private int volunteerGenerateWindowSeconds;
    @Value("${gzly.rate-limit.ai-analysis-limit:6}")
    private int aiAnalysisLimit;
    @Value("${gzly.rate-limit.ai-analysis-window-seconds:60}")
    private int aiAnalysisWindowSeconds;
    @Value("${gzly.rate-limit.feedback-limit:5}")
    private int feedbackLimit;
    @Value("${gzly.rate-limit.feedback-window-seconds:600}")
    private int feedbackWindowSeconds;
    @Value("${gzly.rate-limit.encouragement-limit:6}")
    private int encouragementLimit;
    @Value("${gzly.rate-limit.encouragement-window-seconds:600}")
    private int encouragementWindowSeconds;
    @Value("${gzly.rate-limit.auth-login-limit:5}")
    private int authLoginLimit;
    @Value("${gzly.rate-limit.auth-login-window-seconds:300}")
    private int authLoginWindowSeconds;
    @Value("${gzly.rate-limit.qa-submit-limit:10}")
    private int qaSubmitLimit;
    @Value("${gzly.rate-limit.qa-submit-window-seconds:600}")
    private int qaSubmitWindowSeconds;
    @Value("${gzly.rate-limit.qa-like-limit:60}")
    private int qaLikeLimit;
    @Value("${gzly.rate-limit.qa-like-window-seconds:600}")
    private int qaLikeWindowSeconds;
    @Value("${gzly.rate-limit.algorithm-limit:120}")
    private int algorithmLimit;
    @Value("${gzly.rate-limit.algorithm-window-seconds:60}")
    private int algorithmWindowSeconds;
    @Value("${gzly.rate-limit.site-stats-online-limit:30}")
    private int siteStatsOnlineLimit;
    @Value("${gzly.rate-limit.site-stats-online-window-seconds:60}")
    private int siteStatsOnlineWindowSeconds;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitEnabled) {
            List<PublicRateLimitInterceptor.RateLimitRule> rules = new ArrayList<>();
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "volunteer-generate", "/api/volunteer/generate", "POST", volunteerGenerateLimit, volunteerGenerateWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "ai-analysis", "/api/volunteer/ai-analysis", "GET", aiAnalysisLimit, aiAnalysisWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "feedback", "/api/feedback", "POST", feedbackLimit, feedbackWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "encouragement", "/api/encouragement-messages", "POST", encouragementLimit, encouragementWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "admin-login", "/api/admin/login", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "alumni-login", "/api/alumni/login", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "card-key-activate", "/api/auth/card-key/activate", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "card-key-login", "/api/auth/card-key/login", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "email-send-code", "/api/auth/email/send-code", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "email-login", "/api/auth/email/login", "POST", authLoginLimit, authLoginWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "qa-ask", "/api/qa/ask", "POST", qaSubmitLimit, qaSubmitWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "qa-answer", "/api/qa/answer", "POST", qaSubmitLimit, qaSubmitWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "qa-like", "/api/qa/like/**", "POST", qaLikeLimit, qaLikeWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "algorithm-get", "/api/algorithm/**", "GET", algorithmLimit, algorithmWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "algorithm-post", "/api/algorithm/**", "POST", algorithmLimit, algorithmWindowSeconds));
            rules.add(new PublicRateLimitInterceptor.RateLimitRule(
                    "site-stats-online", "/api/site-stats/online", "GET", siteStatsOnlineLimit, siteStatsOnlineWindowSeconds));
            registry.addInterceptor(new PublicRateLimitInterceptor(redisTemplate, rules));
        }

        registry.addInterceptor(new AuthInterceptor(jwtUtil, "admin"))
                .addPathPatterns("/admin/**")
                .addPathPatterns("/alumni/admin/**")
                .addPathPatterns("/alumni/media/pending")
                .addPathPatterns("/alumni/media/review")
                .addPathPatterns("/alumni/media/review/**")
                .addPathPatterns("/alumni/content/edits")
                .addPathPatterns("/alumni/content/review")
                .addPathPatterns("/alumni/content/review/**")
                .excludePathPatterns("/admin/login");

        registry.addInterceptor(new AuthInterceptor(jwtUtil, "admin", "alumni"))
                .addPathPatterns("/alumni/media/**")
                .addPathPatterns("/alumni/content/edit")
                .addPathPatterns("/alumni/content/my-edits")
                .addPathPatterns("/alumni/qa/reply")
                .addPathPatterns("/alumni/qa/review")
                .addPathPatterns("/alumni/qa/review/update-note")
                .addPathPatterns("/alumni/qa/reply/edit-and-resubmit")
                .addPathPatterns("/alumni/qa/history")
                .addPathPatterns("/alumni/qa/pending");

        // 卡密激活后的"我的空间"端点：user 与 admin 都允许（admin 可代查），但必须有 token
        registry.addInterceptor(new AuthInterceptor(jwtUtil, "user", "admin"))
                .addPathPatterns("/me/**");
    }

    static class AuthInterceptor implements HandlerInterceptor {
        private final JwtUtil jwtUtil;
        private final String[] allowedRoles;

        AuthInterceptor(JwtUtil jwtUtil, String... allowedRoles) {
            this.jwtUtil = jwtUtil;
            this.allowedRoles = allowedRoles;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                if (jwtUtil.isValid(token)) {
                    String role = jwtUtil.getRole(token);
                    boolean roleAllowed = allowedRoles == null || allowedRoles.length == 0;
                    if (!roleAllowed) {
                        for (String allowedRole : allowedRoles) {
                            if (allowedRole.equals(role)) {
                                roleAllowed = true;
                                break;
                            }
                        }
                    }
                    if (!roleAllowed) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该资源\"}");
                        return false;
                    }
                    request.setAttribute("authUserId", jwtUtil.getUserId(token));
                    request.setAttribute("authRole", role);
                    request.setAttribute("authIdentifier", jwtUtil.getIdentifier(token));
                    return true;
                }
            }
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或 Token 已过期\"}");
            return false;
        }
    }
}
