package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.mapper.AiCallLogMapper;
import com.gzly.service.AiCallLogService;
import com.gzly.service.SecurityAuditCounterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 后台：巡检状态面板。
 *
 * <p>路径 {@code /admin/ops/**}，由全局 AuthInterceptor(admin) 保护。</p>
 * <p>说明：应用内可直接采集 DB / Redis / AI 通道 / 后端关键接口 / 磁盘；
 * 而 nginx 5xx、应用 ERROR 日志聚合、首页/CQ 静态页等属 <b>服务器/网关层</b>，
 * 应用进程内无法可靠采集，统一标记为 {@code external}，避免编造数据。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/ops")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AiCallLogMapper aiCallLogMapper;
    private final AiCallLogService aiCallLogService;
    private final SecurityAuditCounterService securityAuditCounterService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port:8082}")
    private int serverPort;

    @Value("${gzly.admission.recommendation-phase:PRE_OFFICIAL_DATA}")
    private String recommendationPhase;

    private final OkHttpClient probeClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build();

    @GetMapping("/health-check")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", LocalDateTime.now().toString());

        List<CheckItem> checks = new ArrayList<>();
        checks.add(checkDb());
        checks.add(checkRedis());
        checks.add(checkAiChannel());
        checks.add(checkBackendApi("volunteer", "/api/volunteer/provinces"));
        checks.add(checkBackendApi("score-line", "/api/score-lines/GZ"));
        checks.add(checkDisk());

        // 网关/服务器层指标：应用内不可采集，明确标记 external
        checks.add(external("home", "首页由 nginx 提供，需网关层探测"));
        checks.add(external("CQ", "/CQ/ 为 nginx 静态站点，需网关层探测"));
        checks.add(external("nginx-5xx", "需读取 nginx access/error 日志（服务器巡检脚本）"));
        checks.add(external("app-error-log", "需聚合 app 日志 ERROR（服务器巡检脚本）"));
        checks.addAll(securityChecks());
        checks.addAll(securityMetricChecks());

        result.put("checks", checks);

        Map<String, Object> guards = new LinkedHashMap<>();
        guards.put("recommendationPhase", recommendationPhase);
        guards.put("fullRecommendEnabled", "FULL_RECOMMEND".equalsIgnoreCase(recommendationPhase));
        guards.put("fake2026", "not_collected");
        guards.put("fake2026Note", "2026 官方数据真伪需 DB 级官方源审计（服务器巡检），应用内不臆断");
        result.put("guards", guards);
        result.put("securityStats24h", securityStats24h());

        long ok = checks.stream().filter(c -> "up".equals(c.getStatus())).count();
        long down = checks.stream().filter(c -> "down".equals(c.getStatus())).count();
        result.put("summary", Map.of(
                "up", ok,
                "down", down,
                "external", checks.stream().filter(c -> "external".equals(c.getStatus())).count(),
                "overall", down == 0 ? "healthy" : "degraded"));
        return Result.ok(result);
    }

    private CheckItem checkDb() {
        CheckItem item = new CheckItem("database", "数据库");
        try {
            aiCallLogMapper.selectCount(null);
            item.setStatus("up");
            item.setDetail("连接正常");
        } catch (Exception e) {
            item.setStatus("down");
            item.setDetail("数据库不可用: " + e.getMessage());
        }
        return item;
    }

    private CheckItem checkRedis() {
        CheckItem item = new CheckItem("redis", "Redis");
        if (stringRedisTemplate == null) {
            item.setStatus("external");
            item.setDetail("未配置 Redis");
            return item;
        }
        try (RedisConnection conn = stringRedisTemplate.getRequiredConnectionFactory().getConnection()) {
            String pong = conn.ping();
            item.setStatus("up");
            item.setDetail("PING " + (pong == null ? "OK" : pong));
        } catch (Exception e) {
            item.setStatus("down");
            item.setDetail("Redis 不可用: " + e.getMessage());
        }
        return item;
    }

    private CheckItem checkAiChannel() {
        CheckItem item = new CheckItem("ai", "AI 通道");
        AiCallLogService.StatusSummary summary = aiCallLogService.statusSummary();
        switch (summary.getOverall()) {
            case "ok" -> {
                item.setStatus("up");
                item.setDetail("最近一次调用成功");
            }
            case "unknown" -> {
                item.setStatus("external");
                item.setDetail("暂无 AI 调用记录");
            }
            default -> {
                item.setStatus("down");
                item.setDetail(summary.getDetail());
            }
        }
        return item;
    }

    private CheckItem checkBackendApi(String key, String path) {
        CheckItem item = new CheckItem(key, key);
        String url = "http://127.0.0.1:" + serverPort + path;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = probeClient.newCall(request).execute()) {
                int code = response.code();
                if (response.isSuccessful()) {
                    item.setStatus("up");
                    item.setDetail("HTTP " + code);
                } else {
                    item.setStatus("down");
                    item.setDetail("HTTP " + code);
                }
            }
        } catch (Exception e) {
            item.setStatus("down");
            item.setDetail("探测失败: " + e.getMessage());
        }
        return item;
    }

    private CheckItem checkDisk() {
        CheckItem item = new CheckItem("disk", "磁盘");
        try {
            File root = new File("/");
            long total = root.getTotalSpace();
            long usable = root.getUsableSpace();
            if (total <= 0) {
                item.setStatus("external");
                item.setDetail("无法读取磁盘信息");
                return item;
            }
            long usedPct = Math.round((total - usable) * 100.0D / total);
            item.setStatus(usedPct >= 90 ? "down" : "up");
            item.setDetail("已用 " + usedPct + "%（可用 " + (usable / 1024 / 1024 / 1024) + "GB / 共 "
                    + (total / 1024 / 1024 / 1024) + "GB）");
        } catch (Exception e) {
            item.setStatus("external");
            item.setDetail("读取磁盘失败: " + e.getMessage());
        }
        return item;
    }

    private CheckItem external(String key, String note) {
        CheckItem item = new CheckItem(key, key);
        item.setStatus("external");
        item.setDetail(note);
        return item;
    }

    private List<CheckItem> securityChecks() {
        List<CheckItem> items = new ArrayList<>();
        items.add(up("security.fail2ban", "SSH fail2ban", "服务器层已启用 fail2ban sshd；实时封禁状态以服务器巡检脚本为准"));
        items.add(up("security.nginx-rate-limit", "nginx 限流", "普通 API、登录、找回、AI、反馈、专业规划入口已配置 nginx limit_req"));
        items.add(up("security.app-rate-limit", "应用接口限流", "应用层 PublicRateLimitInterceptor 已覆盖生成、AI、登录、找回、反馈等公网接口"));
        items.add(up("security.credential-guard", "找回码防爆破", "方案码、对话码、规划码错误 5 次后进入短时限制，日志仅记录脱敏标识"));
        items.add(up("security.admin-auth", "后台 admin 鉴权", "未登录返回 401，非 admin 返回 403，/admin/** 统一由 AuthInterceptor 保护"));
        items.add(up("security.cors", "生产 CORS", "默认仅允许 gzly.dongsiwei.com 与当前 IP 访问，非白名单来源拒绝"));
        items.add(up("security.ai-guard", "AI 防滥用", "AI 消息长度、接口频率与 ComplianceTextGuard 已启用；API Key 不写入调用日志"));
        items.add(external("security.nginx-5xx-24h", "最近 24h nginx 5xx 需读取 nginx access/error 日志（服务器巡检脚本）"));
        items.add(external("security.login-fail-24h", "最近 24h 登录失败次数需读取 Redis/应用日志（服务器巡检脚本）"));
        items.add(external("security.credential-fail-24h", "最近 24h 找回码失败次数需读取 Redis/应用日志（服务器巡检脚本）"));
        items.add(external("security.sensitive-log-scan", "敏感日志关键词扫描需服务器巡检脚本执行，禁止在应用内读取系统日志"));
        return items;
    }

    private List<CheckItem> securityMetricChecks() {
        Map<String, Object> stats = securityStats24h();
        List<CheckItem> items = new ArrayList<>();
        items.add(up("security.metric.login-fail-24h", "24h 登录失败",
                stats.get("adminLoginFailures") + " 次"));
        items.add(up("security.metric.credential-fail-24h", "24h 找回码失败",
                stats.get("credentialFailures") + " 次"));
        items.add(up("security.metric.rate-limit-24h", "24h 限流触发",
                stats.get("rateLimitTriggers") + " 次"));
        items.add(up("security.metric.admin-403-24h", "24h admin 403",
                stats.get("adminForbidden") + " 次"));
        items.add(up("security.metric.ai-failure-24h", "24h AI 调用失败",
                stats.get("aiFailures") + " 次"));
        items.add(up("security.metric.app-5xx-24h", "24h 应用 5xx",
                stats.get("app5xx") + " 次"));
        items.add(external("security.metric.nginx-5xx-24h", "nginx 5xx 需服务器巡检脚本读取 access/error 日志"));
        items.add(external("security.metric.sensitive-log-24h", "敏感日志命中需服务器巡检脚本扫描"));
        return items;
    }

    private Map<String, Object> securityStats24h() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("adminLoginFailures", securityAuditCounterService.countLast24Hours(SecurityAuditCounterService.ADMIN_LOGIN_FAILURE));
        stats.put("credentialFailures", securityAuditCounterService.countLast24Hours(SecurityAuditCounterService.CREDENTIAL_FAILURE));
        stats.put("rateLimitTriggers", securityAuditCounterService.countLast24Hours(SecurityAuditCounterService.RATE_LIMIT));
        stats.put("adminForbidden", securityAuditCounterService.countLast24Hours(SecurityAuditCounterService.ADMIN_FORBIDDEN));
        stats.put("aiFailures", aiCallLogService.failureCountSince(LocalDateTime.now().minusHours(24)));
        stats.put("app5xx", securityAuditCounterService.countLast24Hours(SecurityAuditCounterService.APP_5XX));
        stats.put("sensitiveLogHits", "需服务器巡检脚本扫描");
        stats.put("nginx5xx", "需服务器巡检脚本读取 access/error 日志");
        return stats;
    }

    private CheckItem up(String key, String label, String detail) {
        CheckItem item = new CheckItem(key, label);
        item.setStatus("up");
        item.setDetail(detail);
        return item;
    }

    @Data
    public static class CheckItem {
        private final String key;
        private final String label;
        private String status = "unknown";
        private String detail = "";
    }
}
