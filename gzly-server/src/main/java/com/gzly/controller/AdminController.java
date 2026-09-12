package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.*;
import com.gzly.service.AiConfigService;
import com.gzly.mapper.*;
import com.gzly.service.OfficialLinkPriorityService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.util.ClientIpResolver;
import com.gzly.util.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final BizUserMapper userMapper;
    private final PlanHistoryMapper planMapper;
    private final ScoreLineGzMapper scoreLineMapper;
    private final UniversityMapper universityMapper;
    private final AlumniAdminMapper alumniMapper;
    private final MajorScoreGzMapper majorScoreMapper;
    private final UniOfficialLinkMapper officialLinkMapper;
    private final SpecialAdmissionPolicyMapper specialAdmissionPolicyMapper;
    private final AnnouncementMapper announcementMapper;
    private final BizUserFeedbackMapper feedbackMapper;
    private final EncouragementMessageMapper encouragementMessageMapper;
    private final com.gzly.service.UniversityQaService qaService;
    private final AiConfigService aiConfigService;
    private final OfficialLinkPriorityService officialLinkPriorityService;
    private final VolunteerMetricsRecorder volunteerMetricsRecorder;
    private final JwtUtil jwtUtil;

    @Value("${gzly.admin.password:}")
    private String adminPassword;
    @Value("${gzly.admin.password-hash:}")
    private String adminPasswordHash;
    @Value("${gzly.admin.login-lock-limit:5}")
    private int adminLoginLockLimit;
    @Value("${gzly.admin.login-window-seconds:300}")
    private int adminLoginWindowSeconds;
    @Value("${gzly.admin.login-lock-seconds:900}")
    private int adminLoginLockSeconds;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    @Autowired(required = false)
    private CacheManager cacheManager;

    private static final BCryptPasswordEncoder ADMIN_PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private final Map<String, LocalLoginCounter> localLoginCounters = new ConcurrentHashMap<>();

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String clientIp = getClientIp(httpReq);
        if (isAdminLoginLocked(clientIp)) {
            throw new BizException("登录失败次数过多，请稍后再试");
        }
        if (req.getPassword() == null || !verifyAdminPassword(req.getPassword())) {
            recordAdminLoginFailure(clientIp);
            log.warn("管理后台登录失败: ip={}", clientIp);
            throw new BizException("管理密码错误");
        }
        clearAdminLoginFailures(clientIp);
        log.info("管理后台登录成功: ip={}", clientIp);
        String token = jwtUtil.generateAdmin(0L, "admin");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("role", "admin");
        return Result.ok(result);
    }

    private boolean verifyAdminPassword(String rawPassword) {
        if (adminPasswordHash != null && !adminPasswordHash.isBlank()) {
            try {
                return ADMIN_PASSWORD_ENCODER.matches(rawPassword, adminPasswordHash.trim());
            } catch (IllegalArgumentException e) {
                log.error("管理后台密码哈希格式非法，请检查 GZLY_ADMIN_PASSWORD_HASH");
                return false;
            }
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                adminPassword.getBytes(StandardCharsets.UTF_8),
                rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAdminLoginLocked(String clientIp) {
        String lockKey = adminLoginLockKey(clientIp);
        if (stringRedisTemplate != null) {
            try {
                Boolean locked = stringRedisTemplate.hasKey(lockKey);
                if (Boolean.TRUE.equals(locked)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("读取管理登录锁失败，使用本地登录锁: ip={}", clientIp, e);
            }
        }
        LocalLoginCounter local = localLoginCounters.get(clientIp);
        return local != null && local.lockUntilMs > System.currentTimeMillis();
    }

    private void recordAdminLoginFailure(String clientIp) {
        String failKey = adminLoginFailKey(clientIp);
        String lockKey = adminLoginLockKey(clientIp);
        if (stringRedisTemplate != null) {
            try {
                Long count = stringRedisTemplate.opsForValue().increment(failKey);
                if (count != null && count == 1L) {
                    stringRedisTemplate.expire(failKey, Duration.ofSeconds(adminLoginWindowSeconds));
                }
                if (count != null && count >= adminLoginLockLimit) {
                    stringRedisTemplate.opsForValue().set(lockKey, "1", Duration.ofSeconds(adminLoginLockSeconds));
                }
                return;
            } catch (Exception e) {
                log.warn("写入管理登录失败计数失败，使用本地计数: ip={}", clientIp, e);
            }
        }
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, adminLoginWindowSeconds) * 1000L;
        long lockMs = Math.max(1, adminLoginLockSeconds) * 1000L;
        LocalLoginCounter counter = localLoginCounters.compute(clientIp, (key, existing) -> {
            if (existing == null || existing.expiresAtMs <= now) {
                return new LocalLoginCounter(now + windowMs, 0, 0);
            }
            return existing;
        });
        if (counter.count.incrementAndGet() >= adminLoginLockLimit) {
            counter.lockUntilMs = now + lockMs;
        }
    }

    private void clearAdminLoginFailures(String clientIp) {
        localLoginCounters.remove(clientIp);
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(List.of(adminLoginFailKey(clientIp), adminLoginLockKey(clientIp)));
            } catch (Exception e) {
                log.warn("清理管理登录失败计数失败: ip={}", clientIp, e);
            }
        }
    }

    private String adminLoginFailKey(String clientIp) {
        return "admin:login:fail:" + clientIp;
    }

    private String adminLoginLockKey(String clientIp) {
        return "admin:login:lock:" + clientIp;
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolveOrUnknown(request);
    }

    private static class LocalLoginCounter {
        final long expiresAtMs;
        final AtomicInteger count;
        volatile long lockUntilMs;

        LocalLoginCounter(long expiresAtMs, int count, long lockUntilMs) {
            this.expiresAtMs = expiresAtMs;
            this.count = new AtomicInteger(count);
            this.lockUntilMs = lockUntilMs;
        }
    }

    @Data
    public static class LoginRequest {
        private String password;
    }

    @PostMapping("/cache/evict-volunteer")
    public Result<String> evictVolunteerCaches() {
        if (cacheManager == null) {
            return Result.ok("缓存管理器未启用");
        }
        List<String> cacheNames = List.of(
                "candidateScoreLines",
                "candidateMajorScores",
                "admissionProbabilities",
                "riskAssessments",
                "scorePredictions",
                "rankEstimates",
                "scoreLines",
                "years");
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
        return Result.ok("志愿相关缓存已清理");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalUsers", userMapper.selectCount(null));
        s.put("totalPlans", planMapper.selectCount(null));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        s.put("todayPlans", planMapper.selectCount(
                new LambdaQueryWrapper<PlanHistory>().ge(PlanHistory::getCreatedAt, todayStart)));
        s.put("todayUsers", userMapper.selectCount(
                new LambdaQueryWrapper<BizUser>().ge(BizUser::getCreatedAt, todayStart)));
        s.put("totalUniversities", universityMapper.selectCount(null));
        s.put("totalScoreLines", scoreLineMapper.selectCount(null));
        s.put("totalMajorScoreLines", majorScoreMapper.selectCount(null));
        s.put("totalOfficialLinks", officialLinkMapper.selectCount(
                new LambdaQueryWrapper<UniOfficialLink>().eq(UniOfficialLink::getCaptureStatus, 1)));
        s.put("totalSpecialAdmissionPolicies", specialAdmissionPolicyMapper.selectCount(
                new LambdaQueryWrapper<SpecialAdmissionPolicy>().eq(SpecialAdmissionPolicy::getStatus, 1)));
        s.put("pendingAlumni", alumniMapper.selectCount(
                new LambdaQueryWrapper<AlumniAdmin>().eq(AlumniAdmin::getStatus, 0)));
        s.put("dataQuality", buildDataQualityStats(s));
        s.put("volunteerQuality", buildVolunteerQualityStats());
        return Result.ok(s);
    }

    private Map<String, Object> buildDataQualityStats(Map<String, Object> stats) {
        long totalUniversities = longValue(stats.get("totalUniversities"));
        long totalScoreLines = longValue(stats.get("totalScoreLines"));
        long totalMajorScoreLines = longValue(stats.get("totalMajorScoreLines"));
        long totalOfficialLinks = longValue(stats.get("totalOfficialLinks"));
        long totalSpecialPolicies = longValue(stats.get("totalSpecialAdmissionPolicies"));

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("summary", List.of(
                qualityMetric("院校库", totalUniversities, "所", totalUniversities >= 2000 ? "ok" : "warn"),
                qualityMetric("分数线", totalScoreLines, "条", totalScoreLines >= 30000 ? "ok" : "warn"),
                qualityMetric("专业分", totalMajorScoreLines, "条", totalMajorScoreLines > 0 ? "ok" : "warn"),
                qualityMetric("特殊招生", totalSpecialPolicies, "条", totalSpecialPolicies >= 6 ? "ok" : "warn")
        ));
        quality.put("officialCoverage", percent(totalOfficialLinks, totalUniversities));
        quality.put("officialLinks", Map.of(
                "captured", totalOfficialLinks,
                "missingRows", Math.max(totalUniversities - totalOfficialLinks, 0),
                "missingAdmissionSite", countMissingOfficialField("admission_site"),
                "missingBrochure", countMissingOfficialField("admission_brochure_url"),
                "missingMajorCatalog", countMissingOfficialField("major_catalog_url"),
                "missingTuition", countMissingOfficialField("tuition_summary"),
                "missingAdjustmentRule", countMissingOfficialField("adjustment_rule")
        ));
        quality.put("aiConfig", aiConfigService.getSafeView());
        return quality;
    }

    private Map<String, Object> buildVolunteerQualityStats() {
        Map<String, Long> runtime = volunteerMetricsRecorder.snapshot();
        long total = metric(runtime, VolunteerMetricsRecorder.GENERATE_TOTAL);
        long success = metric(runtime, VolunteerMetricsRecorder.GENERATE_SUCCESS);
        long failure = metric(runtime, VolunteerMetricsRecorder.GENERATE_FAILURE);
        long incomplete = metric(runtime, VolunteerMetricsRecorder.GENERATE_INCOMPLETE);
        long warningTriggered = metric(runtime, VolunteerMetricsRecorder.DATA_QUALITY_WARNING_TRIGGERED);
        long manualReviewTriggered = metric(runtime, VolunteerMetricsRecorder.MANUAL_REVIEW_TRIGGERED);
        long aiTotal = metric(runtime, VolunteerMetricsRecorder.AI_ANALYSIS_TOTAL);
        long aiFailure = metric(runtime, VolunteerMetricsRecorder.AI_ANALYSIS_FAILURE);
        long costTotal = metric(runtime, VolunteerMetricsRecorder.GENERATE_COST_MS_TOTAL);
        long costMax = metric(runtime, VolunteerMetricsRecorder.GENERATE_COST_MS_MAX);

        long persistedWarnings = planMapper.selectCount(new QueryWrapper<PlanHistory>()
                .isNotNull("data_quality_warning")
                .ne("data_quality_warning", ""));
        long persistedManualReviews = planMapper.selectCount(new QueryWrapper<PlanHistory>()
                .isNotNull("manual_review_json")
                .ne("manual_review_json", "")
                .ne("manual_review_json", "[]"));

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("runtime", runtime);
        quality.put("generateTotal", total);
        quality.put("generateSuccess", success);
        quality.put("generateFailure", failure);
        quality.put("generateSuccessRate", percent(success, total));
        quality.put("generateIncomplete", incomplete);
        quality.put("generateIncompleteRate", percent(incomplete, total));
        quality.put("generateAvgCostMs", success > 0 ? Math.round(costTotal * 1.0D / success) : 0);
        quality.put("generateMaxCostMs", costMax);
        quality.put("warningTriggered", warningTriggered);
        quality.put("manualReviewTriggered", manualReviewTriggered);
        quality.put("persistedWarnings", persistedWarnings);
        quality.put("persistedManualReviews", persistedManualReviews);
        quality.put("aiTotal", aiTotal);
        quality.put("aiFailure", aiFailure);
        quality.put("aiFailureRate", percent(aiFailure, aiTotal));
        return quality;
    }

    private long metric(Map<String, Long> metrics, String key) {
        return metrics.getOrDefault(key, 0L);
    }

    private Map<String, Object> qualityMetric(String label, long value, String unit, String status) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("label", label);
        metric.put("value", value);
        metric.put("unit", unit);
        metric.put("status", status);
        return metric;
    }

    private long countMissingOfficialField(String column) {
        return officialLinkMapper.selectCount(new QueryWrapper<UniOfficialLink>()
                .eq("capture_status", 1)
                .and(w -> w.isNull(column).or().eq(column, "")));
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0D / denominator);
    }

    @GetMapping("/ai-config")
    public Result<AiConfigService.AiConfigView> aiConfig() {
        return Result.ok(aiConfigService.getSafeView());
    }

    @PostMapping("/ai-config")
    public Result<AiConfigService.AiConfigView> saveAiConfig(@RequestBody AiConfigService.SaveAiConfigRequest req) {
        return Result.ok(aiConfigService.save(req));
    }

    @PostMapping("/ai-config/test")
    public Result<AiConfigService.AiConfigTestResult> testAiConfig(@RequestBody AiConfigService.SaveAiConfigRequest req) {
        return Result.ok(aiConfigService.testConnection(req));
    }

    @PostMapping("/ai-config/models")
    public Result<AiConfigService.AiModelListResult> aiModels(@RequestBody AiConfigService.SaveAiConfigRequest req) {
        return Result.ok(aiConfigService.listModels(req));
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        page = Math.max(page, 1);
        Page<BizUser> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BizUser>().orderByDesc(BizUser::getCreatedAt));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @GetMapping("/plans")
    public Result<Map<String, Object>> plans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        LambdaQueryWrapper<PlanHistory> qw = new LambdaQueryWrapper<PlanHistory>()
                .orderByDesc(PlanHistory::getCreatedAt);
        if (search != null && !search.isBlank()) {
            qw.and(w -> w.like(PlanHistory::getFirstSubject, search)
                    .or().eq(PlanHistory::getTotalScore, tryParseInt(search)));
        }
        Page<PlanHistory> p = planMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @GetMapping("/universities")
    public Result<Map<String, Object>> universities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        LambdaQueryWrapper<University> qw = new LambdaQueryWrapper<University>()
                .orderByAsc(University::getId);
        if (search != null && !search.isBlank()) {
            qw.like(University::getName, search);
        }
        Page<University> p = universityMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @GetMapping("/official-links")
    public Result<Map<String, Object>> officialLinks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String missingField,
            @RequestParam(defaultValue = "priority") String sortBy,
            @RequestParam(defaultValue = "true") boolean priorityOnly,
            @RequestParam(defaultValue = "30") int windowDays) {
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        LambdaQueryWrapper<University> qw = new LambdaQueryWrapper<University>()
                .orderByAsc(University::getId);
        if (search != null && !search.isBlank()) {
            qw.like(University::getName, search);
        }
        if (province != null && !province.isBlank()) {
            qw.eq(University::getProvince, province.trim());
        }
        List<University> universityList = universityMapper.selectList(qw);

        List<String> schoolIds = universityList.stream()
                .map(University::getSchoolId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, UniOfficialLink> linkMap = schoolIds.isEmpty()
                ? Map.of()
                : officialLinkMapper.selectList(new LambdaQueryWrapper<UniOfficialLink>()
                .in(UniOfficialLink::getSchoolId, schoolIds)).stream()
                .collect(java.util.stream.Collectors.toMap(UniOfficialLink::getSchoolId, v -> v, (a, b) -> a));
        List<UniOfficialLink> officialLinkStatsRows = officialLinkMapper.selectList(new QueryWrapper<UniOfficialLink>()
                .select("school_site",
                        "admission_site",
                        "admission_brochure_url",
                        "major_catalog_url",
                        "tuition_info_url"));

        OfficialLinkPriorityService.PriorityContext priorityContext =
                officialLinkPriorityService.buildPriorityContext(schoolIds, windowDays);

        List<Map<String, Object>> mapped = universityList.stream().map(uni -> {
            UniOfficialLink link = linkMap.get(uni.getSchoolId());
            OfficialLinkPriorityService.PriorityMeta priorityMeta =
                    officialLinkPriorityService.buildPriorityMeta(uni.getSchoolId(), link, priorityContext);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", uni.getId());
            row.put("schoolId", uni.getSchoolId());
            row.put("name", uni.getName());
            row.put("province", uni.getProvince());
            row.put("city", uni.getCity());
            row.put("tags", uni.getTags());
            row.put("schoolSite", uni.getSchoolSite());
            row.put("natureName", uni.getNatureName());
            row.put("officialLink", link);
            row.put("planHitCount", priorityMeta.planHitCount());
            row.put("hotScore", priorityMeta.hotScore());
            row.put("gapCount", priorityMeta.gapCount());
            row.put("missingFields", priorityMeta.missingFields());
            row.put("priorityLevel", priorityMeta.priorityLevel());
            row.put("priorityReasons", priorityMeta.priorityReasons());
            return row;
        }).filter(row -> {
            UniOfficialLink link = (UniOfficialLink) row.get("officialLink");
            int captureStatus = link != null && link.getCaptureStatus() != null ? link.getCaptureStatus() : 0;
            if (status != null && captureStatus != status) {
                return false;
            }
            @SuppressWarnings("unchecked")
            List<String> rowMissingFields = (List<String>) row.get("missingFields");
            if (priorityOnly && (rowMissingFields == null || rowMissingFields.isEmpty())) {
                return false;
            }
            return matchesMissingField(link, missingField);
        }).sorted(buildOfficialLinkComparator(sortBy)).toList();

        long filteredTotal = mapped.size();
        List<Map<String, Object>> items = mapped.stream()
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();

        long totalUniversities = universityMapper.selectCount(null);
        long linkedCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().eq(UniOfficialLink::getCaptureStatus, 1));
        long pendingCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().eq(UniOfficialLink::getCaptureStatus, 2));
        long emptyCount = Math.max(totalUniversities - linkedCount - pendingCount, 0);
        long brochureCount = countUsableDetailLinks(officialLinkStatsRows, UniOfficialLink::getAdmissionBrochureUrl);
        long majorCatalogCount = countUsableDetailLinks(officialLinkStatsRows, UniOfficialLink::getMajorCatalogUrl);
        long tuitionCount = countUsableDetailLinks(officialLinkStatsRows, UniOfficialLink::getTuitionInfoUrl);
        long tuitionSummaryCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getTuitionSummary, ""));
        long majorSummaryCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getMajorCatalogSummary, ""));
        long parsedCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().eq(UniOfficialLink::getParseStatus, 1));
        long adjustmentCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getAdjustmentRule, ""));
        long foreignRuleCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getForeignLanguageRule, ""));
        long physicalRuleCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getPhysicalExamRule, ""));
        long singleSubjectCount = officialLinkMapper.selectCount(new LambdaQueryWrapper<UniOfficialLink>().ne(UniOfficialLink::getSingleSubjectRule, ""));
        long priorityQueueCount = mapped.stream().filter(row -> {
            @SuppressWarnings("unchecked")
            List<String> missingFields = (List<String>) row.get("missingFields");
            return missingFields != null && !missingFields.isEmpty();
        }).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", filteredTotal);
        result.put("page", page);
        result.put("pageSize", size);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUniversities", totalUniversities);
        stats.put("linkedCount", linkedCount);
        stats.put("pendingCount", pendingCount);
        stats.put("emptyCount", emptyCount);
        stats.put("brochureCount", brochureCount);
        stats.put("majorCatalogCount", majorCatalogCount);
        stats.put("tuitionCount", tuitionCount);
        stats.put("tuitionSummaryCount", tuitionSummaryCount);
        stats.put("majorSummaryCount", majorSummaryCount);
        stats.put("parsedCount", parsedCount);
        stats.put("adjustmentCount", adjustmentCount);
        stats.put("foreignRuleCount", foreignRuleCount);
        stats.put("physicalRuleCount", physicalRuleCount);
        stats.put("singleSubjectCount", singleSubjectCount);
        stats.put("activeWindowDays", priorityContext.activeWindowDays());
        stats.put("recentPlanCount", priorityContext.recentPlanCount());
        stats.put("fallbackTriggered", priorityContext.fallbackTriggered());
        stats.put("priorityQueueCount", priorityQueueCount);
        stats.put("priorityP0Count", mapped.stream().filter(row -> "P0".equals(row.get("priorityLevel"))).count());
        stats.put("priorityP1Count", mapped.stream().filter(row -> "P1".equals(row.get("priorityLevel"))).count());
        stats.put("priorityP2Count", mapped.stream().filter(row -> "P2".equals(row.get("priorityLevel"))).count());
        result.put("stats", stats);
        return Result.ok(result);
    }

    @PostMapping("/official-links")
    public Result<UniOfficialLink> saveOfficialLink(@RequestBody OfficialLinkRequest req) {
        if (req.getSchoolId() == null || req.getSchoolId().isBlank()) {
            throw new BizException("schoolId 不能为空");
        }

        String schoolId = req.getSchoolId().trim();
        boolean preserveNonEmpty = Boolean.TRUE.equals(req.getPreserveNonEmpty());

        UniOfficialLink existing = officialLinkMapper.selectOne(new LambdaQueryWrapper<UniOfficialLink>()
                .eq(UniOfficialLink::getSchoolId, schoolId)
                .last("LIMIT 1"));

        UniOfficialLink link = existing != null ? existing : new UniOfficialLink();
        link.setSchoolId(schoolId);
        link.setSchoolName(mergeTextField(req.getSchoolName(), existing != null ? existing.getSchoolName() : null, preserveNonEmpty));
        link.setSourceDomain(mergeTextField(req.getSourceDomain(), existing != null ? existing.getSourceDomain() : null, preserveNonEmpty));
        link.setSchoolSite(mergeTextField(req.getSchoolSite(), existing != null ? existing.getSchoolSite() : null, preserveNonEmpty));
        link.setAdmissionSite(mergeTextField(req.getAdmissionSite(), existing != null ? existing.getAdmissionSite() : null, preserveNonEmpty));
        link.setAdmissionBrochureUrl(mergeTextField(req.getAdmissionBrochureUrl(), existing != null ? existing.getAdmissionBrochureUrl() : null, preserveNonEmpty));
        link.setMajorCatalogUrl(mergeTextField(req.getMajorCatalogUrl(), existing != null ? existing.getMajorCatalogUrl() : null, preserveNonEmpty));
        link.setTuitionInfoUrl(mergeTextField(req.getTuitionInfoUrl(), existing != null ? existing.getTuitionInfoUrl() : null, preserveNonEmpty));
        link.setTuitionRemark(mergeTextField(req.getTuitionRemark(), existing != null ? existing.getTuitionRemark() : null, preserveNonEmpty));
        link.setTuitionSummary(mergeTextField(req.getTuitionSummary(), existing != null ? existing.getTuitionSummary() : null, preserveNonEmpty));
        link.setMajorCatalogSummary(mergeTextField(req.getMajorCatalogSummary(), existing != null ? existing.getMajorCatalogSummary() : null, preserveNonEmpty));
        link.setAdjustmentRule(mergeTextField(req.getAdjustmentRule(), existing != null ? existing.getAdjustmentRule() : null, preserveNonEmpty));
        link.setForeignLanguageRule(mergeTextField(req.getForeignLanguageRule(), existing != null ? existing.getForeignLanguageRule() : null, preserveNonEmpty));
        link.setPhysicalExamRule(mergeTextField(req.getPhysicalExamRule(), existing != null ? existing.getPhysicalExamRule() : null, preserveNonEmpty));
        link.setSingleSubjectRule(mergeTextField(req.getSingleSubjectRule(), existing != null ? existing.getSingleSubjectRule() : null, preserveNonEmpty));
        link.setParserNotes(mergeParserNotes(req.getParserNotes(), existing != null ? existing.getParserNotes() : null, preserveNonEmpty));
        link.setCaptureMethod(resolveCaptureMethod(req.getCaptureMethod(), existing != null ? existing.getCaptureMethod() : null, preserveNonEmpty));
        link.setCaptureStatus(req.getCaptureStatus() == null ? 1 : req.getCaptureStatus());
        if (req.getParseStatus() != null) {
            link.setParseStatus(req.getParseStatus());
        } else if (hasParsedContent(req)) {
            link.setParseStatus(1);
        } else if (existing == null) {
            link.setParseStatus(0);
        }
        link.setLastVerifiedAt(LocalDateTime.now());
        if (link.getParseStatus() != null && link.getParseStatus() > 0) {
            link.setLastParsedAt(LocalDateTime.now());
        }
        if (existing == null) {
            link.setLastCapturedAt(LocalDateTime.now());
            officialLinkMapper.insert(link);
        } else {
            officialLinkMapper.updateById(link);
        }
        return Result.ok(link);
    }

    @PostMapping("/official-links/batch-status")
    public Result<Integer> batchUpdateOfficialLinkStatus(@RequestBody BatchOfficialStatusRequest req) {
        if (req.getSchoolIds() == null || req.getSchoolIds().isEmpty()) {
            throw new BizException("schoolIds 不能为空");
        }
        if (req.getCaptureStatus() == null) {
            throw new BizException("captureStatus 不能为空");
        }

        int updated = 0;
        for (String schoolId : req.getSchoolIds()) {
            if (schoolId == null || schoolId.isBlank()) continue;
            UniOfficialLink existing = officialLinkMapper.selectOne(new LambdaQueryWrapper<UniOfficialLink>()
                    .eq(UniOfficialLink::getSchoolId, schoolId)
                    .last("LIMIT 1"));
            if (existing == null) continue;
            existing.setCaptureStatus(req.getCaptureStatus());
            existing.setLastVerifiedAt(LocalDateTime.now());
            officialLinkMapper.updateById(existing);
            updated++;
        }
        return Result.ok(updated);
    }

    @GetMapping("/announcements")
    public Result<Map<String, Object>> announcements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getSortOrder)
                .orderByDesc(Announcement::getPublishedAt)
                .orderByDesc(Announcement::getId);
        if (status != null) {
            qw.eq(Announcement::getStatus, status);
        }
        Page<Announcement> p = announcementMapper.selectPage(new Page<>(page, Math.min(size, 100)), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @PostMapping("/announcements")
    public Result<Announcement> saveAnnouncement(@RequestBody AnnouncementRequest req, HttpServletRequest request) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("标题不能为空");
        }
        if (req.getContentMd() == null || req.getContentMd().isBlank()) {
            throw new BizException("公告内容不能为空");
        }

        Announcement announcement = req.getId() == null
                ? new Announcement()
                : announcementMapper.selectById(req.getId());
        if (announcement == null) {
            throw new BizException("公告不存在");
        }

        Long userId = getAuthUserId(request);
        announcement.setTitle(req.getTitle().trim());
        announcement.setContentMd(req.getContentMd().trim());
        announcement.setPopupEnabled(req.getPopupEnabled() == null ? 1 : req.getPopupEnabled());
        announcement.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        Integer targetStatus = req.getStatus() == null ? 0 : req.getStatus();
        announcement.setStatus(targetStatus);
        announcement.setUpdatedBy(userId);
        if (announcement.getId() == null) {
            announcement.setCreatedBy(userId);
            if (targetStatus == 1) {
                announcement.setPublishedAt(LocalDateTime.now());
            }
            announcementMapper.insert(announcement);
        } else {
            if (targetStatus == 1 && announcement.getPublishedAt() == null) {
                announcement.setPublishedAt(LocalDateTime.now());
            }
            if (targetStatus != 1 && req.getPublishedAt() == null) {
                announcement.setPublishedAt(announcement.getPublishedAt());
            }
            announcementMapper.updateById(announcement);
        }
        return Result.ok(announcement);
    }

    @PostMapping("/announcements/publish")
    public Result<Announcement> publishAnnouncement(@RequestBody AnnouncementPublishRequest req, HttpServletRequest request) {
        if (req.getId() == null) throw new BizException("公告ID不能为空");
        Announcement announcement = announcementMapper.selectById(req.getId());
        if (announcement == null) throw new BizException("公告不存在");
        announcement.setStatus(Boolean.TRUE.equals(req.getPublished()) ? 1 : 2);
        if (Boolean.TRUE.equals(req.getPublished())) {
            announcement.setPublishedAt(LocalDateTime.now());
        }
        announcement.setUpdatedBy(getAuthUserId(request));
        announcementMapper.updateById(announcement);
        return Result.ok(announcement);
    }

    @GetMapping("/feedbacks")
    public Result<Map<String, Object>> feedbacks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        LambdaQueryWrapper<BizUserFeedback> qw = new LambdaQueryWrapper<BizUserFeedback>()
                .orderByAsc(BizUserFeedback::getStatus)
                .orderByDesc(BizUserFeedback::getCreatedAt);
        if (status != null) {
            qw.eq(BizUserFeedback::getStatus, status);
        }

        Page<BizUserFeedback> p = feedbackMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        result.put("unreadCount", feedbackMapper.selectCount(
                new LambdaQueryWrapper<BizUserFeedback>().eq(BizUserFeedback::getStatus, 0)));
        return Result.ok(result);
    }

    @PostMapping("/feedbacks/read")
    public Result<BizUserFeedback> markFeedbackRead(@RequestBody FeedbackReadRequest req) {
        if (req.getId() == null) {
            throw new BizException("反馈ID不能为空");
        }
        BizUserFeedback feedback = feedbackMapper.selectById(req.getId());
        if (feedback == null) {
            throw new BizException("反馈不存在");
        }
        boolean read = req.getRead() == null || req.getRead();
        feedback.setStatus(read ? 1 : 0);
        feedback.setReadAt(read ? LocalDateTime.now() : null);
        feedbackMapper.updateById(feedback);
        return Result.ok(feedback);
    }

    @GetMapping("/encouragement-messages")
    public Result<Map<String, Object>> encouragementMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        LambdaQueryWrapper<EncouragementMessage> qw = new LambdaQueryWrapper<EncouragementMessage>()
                .orderByDesc(EncouragementMessage::getCreatedAt);
        if (status != null) {
            qw.eq(EncouragementMessage::getStatus, status);
        }

        Page<EncouragementMessage> p = encouragementMessageMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords().stream().map(AdminEncouragementMessageView::from).toList());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        result.put("visibleCount", encouragementMessageMapper.selectCount(
                new LambdaQueryWrapper<EncouragementMessage>().eq(EncouragementMessage::getStatus, 1)));
        result.put("hiddenCount", encouragementMessageMapper.selectCount(
                new LambdaQueryWrapper<EncouragementMessage>().eq(EncouragementMessage::getStatus, 0)));
        return Result.ok(result);
    }

    @DeleteMapping("/encouragement-messages/{id}")
    public Result<String> deleteEncouragementMessage(@PathVariable Long id) {
        EncouragementMessage message = encouragementMessageMapper.selectById(id);
        if (message == null) {
            throw new BizException("留言不存在");
        }
        if (!Objects.equals(message.getStatus(), 0)) {
            message.setStatus(0);
            message.setUpdatedAt(LocalDateTime.now());
            encouragementMessageMapper.updateById(message);
        }
        return Result.ok("已下架");
    }

    @GetMapping("/score-lines")
    public Result<Map<String, Object>> scoreLines(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String subjectType) {
        LambdaQueryWrapper<ScoreLineGz> qw = new LambdaQueryWrapper<ScoreLineGz>()
                .orderByDesc(ScoreLineGz::getYear)
                .orderByAsc(ScoreLineGz::getUniversityName);
        if (search != null && !search.isBlank()) {
            qw.like(ScoreLineGz::getUniversityName, search);
        }
        if (year != null) {
            qw.eq(ScoreLineGz::getYear, year);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(ScoreLineGz::getSubjectType, subjectType);
        }
        Page<ScoreLineGz> p = scoreLineMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @GetMapping("/major-scores")
    public Result<Map<String, Object>> majorScores(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String subjectType) {
        LambdaQueryWrapper<MajorScoreGz> qw = new LambdaQueryWrapper<MajorScoreGz>()
                .orderByDesc(MajorScoreGz::getYear)
                .orderByAsc(MajorScoreGz::getUniversityName);
        if (search != null && !search.isBlank()) {
            qw.and(w -> w.like(MajorScoreGz::getUniversityName, search)
                    .or().like(MajorScoreGz::getMajorName, search));
        }
        if (year != null) {
            qw.eq(MajorScoreGz::getYear, year);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(MajorScoreGz::getSubjectType, subjectType);
        }
        Page<MajorScoreGz> p = majorScoreMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    // ── 问答管理 ──

    @GetMapping("/qa/pending")
    public Result<Map<String, Object>> qaPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var p = qaService.listPending(page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @PostMapping("/qa/review")
    public Result<String> qaReview(@RequestBody QaReviewRequest req) {
        if (req.getId() == null) throw new BizException("审核ID不能为空");
        if (req.getStatus() == null) throw new BizException("审核状态不能为空");
        qaService.review(req.getId(), req.getStatus(), req.getReason());
        return Result.ok(req.getStatus() == 1 ? "已通过" : "已退回");
    }

    @PostMapping("/qa/review/batch")
    public Result<Map<String, Object>> qaReviewBatch(@RequestBody QaBatchReviewRequest req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            throw new BizException("请选择至少一条问答记录");
        }
        if (req.getApproved() == null) throw new BizException("审核动作不能为空");
        int status = Boolean.TRUE.equals(req.getApproved()) ? 1 : 2;
        var reviewResult = qaService.batchReview(req.getIds(), status, req.getReason());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processedCount", reviewResult.getProcessedCount());
        result.put("processedIds", reviewResult.getProcessedIds());
        return Result.ok(result);
    }

    @DeleteMapping("/qa/{id}")
    public Result<String> qaDelete(@PathVariable Long id) {
        qaService.delete(id);
        return Result.ok("已删除");
    }

    @GetMapping("/qa/count")
    public Result<Long> qaCount() {
        return Result.ok(qaService.countPending());
    }

    @GetMapping("/qa/list")
    public Result<Map<String, Object>> qaList(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var p = qaService.listAll(schoolId, status, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        return Result.ok(result);
    }

    @GetMapping("/qa/monitor/schools")
    public Result<Map<String, Object>> qaMonitorSchools(
            @RequestParam(defaultValue = "all") String filter) {
        List<com.gzly.service.UniversityQaService.QaMonitorSchoolVO> items = qaService.listMonitorSchools(filter);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schoolCount", items.size());
        summary.put("highRiskCount", items.stream().filter(com.gzly.service.UniversityQaService.QaMonitorSchoolVO::isHighRisk).count());
        summary.put("manualReviewCount", items.stream().mapToInt(com.gzly.service.UniversityQaService.QaMonitorSchoolVO::getManualReviewCount24h).sum());
        summary.put("autoRejectedCount", items.stream().mapToInt(com.gzly.service.UniversityQaService.QaMonitorSchoolVO::getAutoRejectedCount24h).sum());
        summary.put("approvedCount", items.stream().mapToInt(com.gzly.service.UniversityQaService.QaMonitorSchoolVO::getApprovedCount24h).sum());
        summary.put("trend", qaService.listSevenDayTrend(null));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("summary", summary);
        return Result.ok(result);
    }

    @GetMapping("/qa/monitor/logs")
    public Result<Map<String, Object>> qaMonitorLogs(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var p = qaService.listMonitorLogs(schoolId, status, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("pageSize", size);
        return Result.ok(result);
    }

    @PostMapping("/qa/monitor/hide")
    public Result<Void> qaMonitorHide(@RequestBody QaMonitorHideRequest req, HttpServletRequest request) {
        if (req.getId() == null) throw new BizException("问答ID不能为空");
        if (req.getReason() == null || req.getReason().isBlank()) throw new BizException("下架原因不能为空");
        qaService.hideQaByAdmin(req.getId(), req.getReason(), getAuthUserId(request));
        return Result.ok();
    }

    @PostMapping("/qa/monitor/toggle-school")
    public Result<Void> qaMonitorToggleSchool(@RequestBody QaMonitorToggleSchoolRequest req, HttpServletRequest request) {
        if (req.getSchoolId() == null || req.getSchoolId().isBlank()) throw new BizException("schoolId不能为空");
        if (req.getDisabled() == null) throw new BizException("disabled不能为空");
        qaService.toggleSchoolQa(req.getSchoolId(), req.getDisabled(), req.getReason(), req.getDisabledUntil(), getAuthUserId(request));
        return Result.ok();
    }

    private int tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    @Data
    public static class QaReviewRequest {
        private Long id;
        private Integer status;
        private String reason;
    }

    @Data
    public static class QaBatchReviewRequest {
        private List<Long> ids;
        private Boolean approved;
        private String reason;
    }

    @Data
    public static class QaMonitorHideRequest {
        private Long id;
        private String reason;
    }

    @Data
    public static class QaMonitorToggleSchoolRequest {
        private String schoolId;
        private Boolean disabled;
        private String reason;
        private LocalDateTime disabledUntil;
    }

    private boolean hasParsedContent(OfficialLinkRequest req) {
        return isNotBlank(req.getTuitionSummary())
                || isNotBlank(req.getMajorCatalogSummary())
                || isNotBlank(req.getAdjustmentRule())
                || isNotBlank(req.getForeignLanguageRule())
                || isNotBlank(req.getPhysicalExamRule())
                || isNotBlank(req.getSingleSubjectRule())
                || isNotBlank(req.getParserNotes());
    }

    private String mergeTextField(String incomingValue, String existingValue, boolean preserveNonEmpty) {
        String normalizedIncoming = normalizeTextField(incomingValue);
        String normalizedExisting = normalizeTextField(existingValue);
        if (preserveNonEmpty && isNotBlank(normalizedExisting)) {
            return normalizedExisting;
        }
        return normalizedIncoming;
    }

    private String mergeParserNotes(String incomingValue, String existingValue, boolean preserveNonEmpty) {
        String normalizedIncoming = normalizeTextField(incomingValue);
        String normalizedExisting = normalizeTextField(existingValue);
        if (!preserveNonEmpty) {
            return mergeDistinctNotes(normalizedIncoming);
        }
        if (isBlank(normalizedExisting)) {
            return mergeDistinctNotes(normalizedIncoming);
        }
        if (isBlank(normalizedIncoming)) {
            return normalizedExisting;
        }
        return mergeDistinctNotes(normalizedExisting, normalizedIncoming);
    }

    private String mergeDistinctNotes(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeTextField(value);
            if (isBlank(normalized)) {
                continue;
            }
            for (String segment : normalized.split("\\n\\s*\\n")) {
                String normalizedSegment = normalizeTextField(segment);
                if (isNotBlank(normalizedSegment)) {
                    parts.add(normalizedSegment);
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", parts);
    }

    private String resolveCaptureMethod(String incomingValue, String existingValue, boolean preserveNonEmpty) {
        String normalizedExisting = normalizeTextField(existingValue);
        if (preserveNonEmpty && isNotBlank(normalizedExisting)) {
            return normalizedExisting;
        }
        String normalizedIncoming = normalizeTextField(incomingValue);
        return isBlank(normalizedIncoming) ? "manual" : normalizedIncoming;
    }

    private String normalizeTextField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private boolean matchesMissingField(UniOfficialLink link, String missingField) {
        if (missingField == null || missingField.isBlank()) {
            return true;
        }
        return switch (missingField) {
            case "admissionSite" -> link == null || !hasUsableOfficialUrl(link.getAdmissionSite());
            case "admissionBrochureUrl" -> isMissingDetailField(link, UniOfficialLink::getAdmissionBrochureUrl);
            case "majorCatalogUrl" -> isMissingDetailField(link, UniOfficialLink::getMajorCatalogUrl);
            case "tuitionInfoUrl" -> isMissingDetailField(link, UniOfficialLink::getTuitionInfoUrl);
            case "parsedContent" -> link == null || link.getParseStatus() == null || link.getParseStatus() != 1;
            case "tuitionSummary" -> link == null || isBlank(link.getTuitionSummary());
            case "majorCatalogSummary" -> link == null || isBlank(link.getMajorCatalogSummary());
            case "adjustmentRule" -> link == null || isBlank(link.getAdjustmentRule());
            case "foreignLanguageRule" -> link == null || isBlank(link.getForeignLanguageRule());
            case "physicalExamRule" -> link == null || isBlank(link.getPhysicalExamRule());
            case "singleSubjectRule" -> link == null || isBlank(link.getSingleSubjectRule());
            default -> true;
        };
    }

    private long countUsableDetailLinks(List<UniOfficialLink> links, Function<UniOfficialLink, String> getter) {
        if (links == null || links.isEmpty()) {
            return 0L;
        }
        return links.stream()
                .filter(link -> !isMissingDetailField(link, getter))
                .count();
    }

    private boolean isMissingDetailField(UniOfficialLink link, Function<UniOfficialLink, String> getter) {
        if (link == null) {
            return true;
        }
        return !OfficialLinkPriorityService.hasUsableDetailUrl(
                getter.apply(link),
                link.getSchoolSite(),
                link.getAdmissionSite()
        );
    }

    private boolean hasUsableOfficialUrl(String value) {
        return !OfficialLinkPriorityService.sanitizeOfficialUrl(value).isEmpty();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Comparator<Map<String, Object>> buildOfficialLinkComparator(String sortBy) {
        Comparator<Map<String, Object>> hotComparator = Comparator
                .comparingInt((Map<String, Object> row) -> getIntValue(row.get("planHitCount"))).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) -> getIntValue(row.get("gapCount"))).reversed())
                .thenComparing(row -> String.valueOf(row.get("schoolId")), Comparator.nullsLast(String::compareTo));

        if ("id".equalsIgnoreCase(sortBy)) {
            return Comparator
                    .comparingLong((Map<String, Object> row) -> getLongValue(row.get("id")))
                    .thenComparing(row -> String.valueOf(row.get("schoolId")), Comparator.nullsLast(String::compareTo));
        }
        if ("hot".equalsIgnoreCase(sortBy)) {
            return hotComparator;
        }
        return Comparator
                .comparingInt((Map<String, Object> row) -> priorityLevelRank(String.valueOf(row.get("priorityLevel"))))
                .thenComparing(hotComparator);
    }

    private int priorityLevelRank(String priorityLevel) {
        return switch (priorityLevel) {
            case "P0" -> 0;
            case "P1" -> 1;
            default -> 2;
        };
    }

    private int getIntValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private long getLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getAuthUserId(HttpServletRequest request) {
        Object value = request.getAttribute("authUserId");
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        return 0L;
    }

    @Data
    public static class OfficialLinkRequest {
        private String schoolId;
        private String schoolName;
        private String sourceDomain;
        private String schoolSite;
        private String admissionSite;
        private String admissionBrochureUrl;
        private String majorCatalogUrl;
        private String tuitionInfoUrl;
        private String tuitionRemark;
        private String tuitionSummary;
        private String majorCatalogSummary;
        private String adjustmentRule;
        private String foreignLanguageRule;
        private String physicalExamRule;
        private String singleSubjectRule;
        private String parserNotes;
        private String captureMethod;
        private Integer captureStatus;
        private Integer parseStatus;
        private Boolean preserveNonEmpty;
    }

    @Data
    public static class BatchOfficialStatusRequest {
        private List<String> schoolIds;
        private Integer captureStatus;
    }

    @Data
    public static class AnnouncementRequest {
        private Long id;
        private String title;
        private String contentMd;
        private Integer status;
        private Integer popupEnabled;
        private Integer sortOrder;
        private LocalDateTime publishedAt;
    }

    @Data
    public static class AnnouncementPublishRequest {
        private Long id;
        private Boolean published;
    }

    @Data
    public static class FeedbackReadRequest {
        private Long id;
        private Boolean read;
    }

    @Data
    public static class AdminEncouragementMessageView {
        private Long id;
        private String nickname;
        private String content;
        private Integer status;
        private String createdAt;
        private String updatedAt;

        static AdminEncouragementMessageView from(EncouragementMessage message) {
            AdminEncouragementMessageView view = new AdminEncouragementMessageView();
            view.setId(message.getId());
            view.setNickname(message.getNickname());
            view.setContent(message.getContent());
            view.setStatus(message.getStatus());
            view.setCreatedAt(message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
            view.setUpdatedAt(message.getUpdatedAt() == null ? "" : message.getUpdatedAt().toString());
            return view;
        }
    }
}
