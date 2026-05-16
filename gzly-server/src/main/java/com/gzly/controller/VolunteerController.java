package com.gzly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.entity.PlanHistory;
import com.gzly.service.AiService;
import com.gzly.service.AlgorithmService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProvinceRankService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.service.VolunteerService;
import com.gzly.service.VolunteerService.GenerateRequest;
import com.gzly.service.VolunteerService.PlanResult;
import com.gzly.service.VolunteerService.VolunteerItem;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class VolunteerController {

    private final VolunteerService volunteerService;
    private final ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    private final AiService aiService;
    private final AlgorithmService algorithmService;
    private final ProvincePolicyService provincePolicyService;
    private final ProvinceRankService provinceRankService;
    private final VolunteerMetricsRecorder metricsRecorder;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String SAFETY_CODE_FORBIDDEN_MESSAGE = "安全码错误或无权访问该方案";

    @Value("${gzly.stability.ai-analysis-active-global-limit:30}")
    private int aiAnalysisActiveGlobalLimit;
    @Value("${gzly.stability.ai-analysis-active-per-ip-limit:2}")
    private int aiAnalysisActivePerIpLimit;
    @Value("${gzly.stability.ai-analysis-active-ttl-seconds:180}")
    private int aiAnalysisActiveTtlSeconds;
    /** 同一 planId 的 AI 解读互斥锁 TTL，覆盖一次完整 SSE 调用的最长时间。 */
    @Value("${gzly.stability.ai-analysis-plan-lock-ttl-seconds:120}")
    private int aiAnalysisPlanLockTtlSeconds;

    @PostMapping("/generate")
    public Result<PlanResult> generate(@RequestBody GenerateRequest req, HttpServletRequest httpReq) {
        Long userId = tryExtractUserId(httpReq);
        String clientIp = getClientIp(httpReq);
        String provinceCode = provincePolicyService.normalizeProvinceCode(req == null ? null : req.getProvinceCode());
        PlanResult result = provincePolicyService.isProfessionalGroupProvince(provinceCode)
                ? professionalGroupVolunteerService.generate(req, userId, clientIp)
                : volunteerService.generate(req, userId, clientIp);
        return Result.ok(result);
    }

    @GetMapping("/provinces")
    public Result<List<ProvincePolicyService.ProvincePolicy>> provinces() {
        return Result.ok(provincePolicyService.listPolicies());
    }

    @GetMapping("/plan")
    public Result<PlanResult> plan(@RequestParam Long planId,
                                   @RequestParam(required = false) String safetyCode,
                                   @RequestParam(required = false) String accessKey,
                                   @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                   @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey) {
        return doFetchPlan(planId, firstNonBlank(safetyCode, headerSafetyCode, accessKey, headerAccessKey));
    }

    /**
     * 通过请求体恢复方案，避免 accessKey 出现在 URL / 访问日志 / Referer 中。
     * GET 版本仅保留兼容旧链接，新前端统一走该接口。
     */
    @PostMapping("/plan")
    public Result<PlanResult> planByBody(@RequestBody PlanAccessRequest req) {
        if (req == null) {
            throw new BizException("方案参数不能为空");
        }
        return doFetchPlan(req.getPlanId(), firstNonBlank(req.getSafetyCode(), req.getAccessKey()));
    }

    private Result<PlanResult> doFetchPlan(Long planId, String accessKey) {
        PlanResult result = volunteerService.getPlanResult(planId, accessKey);
        if (result == null) {
            throw new BizException("方案不存在或访问密钥无效");
        }
        return Result.ok(result);
    }

    @lombok.Data
    public static class PlanAccessRequest {
        private Long planId;
        private String safetyCode;
        private String accessKey;
    }

    @GetMapping("/history")
    public Result<List<PlanResult>> history(@RequestParam String identifier, HttpServletRequest httpReq) {
        Long userId = tryExtractUserId(httpReq);
        if (userId == null) return Result.ok(List.of());
        return Result.ok(volunteerService.getHistory(userId));
    }

    /**
     * 用户手填位次的官方校验入口。
     * 系统不会自动写入估算位次，仅返回区间提示并告知考生以官方一分一段表为准。
     */
    @GetMapping("/rank-check")
    public Result<RankCheckResponse> rankCheck(
            @RequestParam(required = false, defaultValue = "GZ") String provinceCode,
            @RequestParam int totalScore,
            @RequestParam(required = false) Integer provinceRank,
            @RequestParam String firstSubject,
            @RequestParam(required = false) Integer referenceYear) {
        if (totalScore <= 0 || totalScore > 750) {
            throw new BizException("高考总分必须在1-750之间");
        }
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(provinceCode);
        String subjectType = "历史".equals(firstSubject) ? "历史类" : "物理类";
        AlgorithmService.RankEstimate estimate = provinceRankService.estimateRank(policy.getProvinceCode(), totalScore, subjectType, referenceYear);

        RankCheckResponse resp = new RankCheckResponse();
        resp.setProvinceCode(policy.getProvinceCode());
        resp.setProvinceName(policy.getProvinceName());
        resp.setSubjectType(subjectType);
        resp.setReferenceYear(estimate.getReferenceYear());
        resp.setRankLow(estimate.getRankLow());
        resp.setRankHigh(estimate.getRankHigh());
        resp.setSourceName(estimate.getSource());
        resp.setSourceUrl(estimate.getSourceUrl());
        resp.setSourcePageUrl(estimate.getSourcePageUrl());
        resp.setParseMethod(estimate.getParseMethod());
        resp.setNote(estimate.getNote());
        resp.setOfficialDataReady(estimate.getDataPoints() > 0);

        if (provinceRank != null && provinceRank > 0
                && estimate.getDataPoints() > 0
                && estimate.getRankLow() > 0 && estimate.getRankHigh() > 0) {
            int low = Math.max(1, estimate.getRankLow());
            int high = Math.max(low, estimate.getRankHigh());
            resp.setSubmittedRank(provinceRank);
            resp.setMatched(provinceRank >= low && provinceRank <= high);
        }
        // 永远显式提示用户：系统不会替考生写入位次
        resp.setReminder(String.format("系统仅做参考校验，最终请以%s公布的官方一分一段表与考生本人确认的位次为准。",
                policy.getOfficialSourceName()));
        return Result.ok(resp);
    }

    /**
     * 暴露关键监控指标（生成成功率、耗时、复核率、AI 失败率等）。
     * 仅运维/管理员需要消费，前端不直接调用，因此不强制鉴权但建议在 Nginx/网关层加白名单。
     */
    @GetMapping("/metrics")
    public Result<java.util.Map<String, Long>> metrics() {
        return Result.ok(metricsRecorder.snapshot());
    }

    /**
     * Rank-check 响应体。
     */
    @lombok.Data
    public static class RankCheckResponse {
        private String provinceCode;
        private String provinceName;
        private String subjectType;
        private Integer referenceYear;
        private Integer rankLow;
        private Integer rankHigh;
        private Integer submittedRank;
        private Boolean matched;
        private boolean officialDataReady;
        private String sourceName;
        private String sourceUrl;
        private String sourcePageUrl;
        private String parseMethod;
        private String note;
        private String reminder;
    }

    /**
     * 为 SSE 解读换发短效一次性凭证。
     * EventSource 只能发 GET，因此 query 中只放短效 ticket，不再暴露长期 accessKey。
     */
    @PostMapping("/ai-analysis-ticket")
    public Result<AiAnalysisTicketResponse> aiAnalysisTicket(@RequestBody AiAnalysisTicketRequest req) {
        String credential = req == null ? "" : firstNonBlank(req.getSafetyCode(), req.getAccessKey());
        if (req == null || req.getPlanId() == null || credential.isBlank()) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        PlanHistory plan = volunteerService.getPlanById(req.getPlanId());
        if (plan == null || !volunteerService.isValidPlanAccessKey(req.getPlanId(), credential)) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        String key = "volunteer:ai-ticket:" + ticket;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", req.getPlanId());
        payload.put("safetyCode", credential);
        payload.put("profile", req.getProfile() == null ? "" : req.getProfile());
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(payload),
                    Duration.ofSeconds(120));
        } catch (Exception e) {
            throw new BizException("AI解读凭证服务暂不可用，请稍后再试");
        }
        AiAnalysisTicketResponse response = new AiAnalysisTicketResponse();
        response.setTicket(ticket);
        response.setExpiresInSeconds(120);
        return Result.ok(response);
    }

    @lombok.Data
    public static class AiAnalysisTicketRequest {
        private Long planId;
        private String safetyCode;
        private String accessKey;
        private String profile;
    }

    @lombok.Data
    public static class AiAnalysisTicketResponse {
        private String ticket;
        private int expiresInSeconds;
    }

    @PostMapping("/zhangxuefeng-skills-chat")
    public Result<AdvisorSkillChatResponse> zhangxuefengSkillsChat(@RequestBody AdvisorSkillChatRequest req,
                                                                   HttpServletRequest httpReq) {
        String credential = req == null ? "" : firstNonBlank(req.getSafetyCode(), req.getAccessKey());
        if (req == null || req.getPlanId() == null || credential.isBlank()) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }
        String message = req.getMessage() == null ? "" : req.getMessage().trim();
        if (message.length() < 2) {
            throw new BizException("请输入要咨询的问题");
        }
        if (message.length() > 800) {
            throw new BizException("问题太长，请控制在800字以内");
        }

        PlanHistory plan = volunteerService.getPlanById(req.getPlanId());
        if (plan == null || !volunteerService.isValidPlanAccessKey(req.getPlanId(), credential)) {
            throw new BizException(403, SAFETY_CODE_FORBIDDEN_MESSAGE);
        }

        String clientIp = getClientIp(httpReq);
        String globalKey = "active:advisor-skill:global";
        String ipKey = "active:advisor-skill:ip:" + clientIp;
        if (!tryAcquireAiAnalysisSlot(globalKey, ipKey)) {
            throw new BizException("当前AI咨询请求较多，请稍后再试");
        }
        AtomicBoolean released = new AtomicBoolean(false);
        try {
            String reply = aiService.chatWithAdvisorSkill(
                    buildAdvisorChatSummary(plan),
                    req.getAiReport(),
                    message,
                    req.getMessages());
            AdvisorSkillChatResponse response = new AdvisorSkillChatResponse();
            response.setReply(reply);
            response.setSourceProjectName(VolunteerService.ADVISOR_SOURCE_PROJECT_NAME);
            response.setSourceProjectUrl(VolunteerService.ADVISOR_SOURCE_PROJECT_URL);
            response.setSourceNote(VolunteerService.ADVISOR_SOURCE_NOTE);
            return Result.ok(response);
        } finally {
            releaseAiAnalysisSlot(globalKey, ipKey, released);
        }
    }

    @lombok.Data
    public static class AdvisorSkillChatRequest {
        private Long planId;
        private String safetyCode;
        private String accessKey;
        private String message;
        private String aiReport;
        private List<AiService.AdvisorSkillChatMessage> messages;
    }

    @lombok.Data
    public static class AdvisorSkillChatResponse {
        private String reply;
        private String sourceProjectName;
        private String sourceProjectUrl;
        private String sourceNote;
    }

    /**
     * AI 深度解读 — SSE 流式响应
     */
    @GetMapping(value = "/ai-analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiAnalysis(@RequestParam(required = false) Long planId,
                                 @RequestParam(required = false) String safetyCode,
                                 @RequestParam(required = false) String accessKey,
                                 @RequestParam(required = false) String profile,
                                 @RequestParam(required = false) String ticket,
                                 HttpServletRequest httpReq) {
        SseEmitter emitter = new SseEmitter(120_000L);

        AiTicketPayload ticketPayload = consumeAiTicket(ticket);
        if (ticketPayload != null) {
            planId = ticketPayload.planId();
            safetyCode = ticketPayload.safetyCode();
            profile = ticketPayload.profile();
        }

        String credential = firstNonBlank(safetyCode, accessKey);
        if (planId == null || credential.isBlank()) {
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 方案访问凭证缺失"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        PlanHistory plan = volunteerService.getPlanById(planId);
        if (plan == null || !volunteerService.isValidPlanAccessKey(planId, credential)) {
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 方案不存在"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        // 构建摘要
        String summary = buildPlanSummary(plan);
        if (profile != null && !profile.isBlank()) {
            summary += "\n\n考生决策偏好：" + profile;
        }
        final String finalSummary = summary;
        String clientIp = getClientIp(httpReq);
        String globalKey = "active:ai-analysis:global";
        String ipKey = "active:ai-analysis:ip:" + clientIp;
        String planLockKey = "active:ai-analysis:plan:" + planId;
        // planId 互斥：同一方案同时只允许一路 SSE，否则用户多次点击 / 多 tab 会重复消耗 OpenAI token。
        if (!tryAcquireAiAnalysisPlanLock(planLockKey)) {
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 该方案的 AI 解读正在生成中，请等待结果或稍后再试"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
        if (!tryAcquireAiAnalysisSlot(globalKey, ipKey)) {
            releaseAiAnalysisPlanLock(planLockKey);
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 当前AI解读请求较多，请稍后再试"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaser = () -> {
            releaseAiAnalysisSlot(globalKey, ipKey, released);
            releaseAiAnalysisPlanLock(planLockKey);
        };
        emitter.onCompletion(releaser);
        emitter.onTimeout(releaser);
        emitter.onError((ex) -> releaser.run());

        try {
            taskExecutor.execute(() -> aiService.streamAnalysis(emitter, finalSummary));
        } catch (RejectedExecutionException e) {
            releaser.run();
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 当前系统繁忙，请稍后再试"));
            } catch (Exception ignored) {}
            emitter.complete();
        }

        return emitter;
    }

    private AiTicketPayload consumeAiTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String key = "volunteer:ai-ticket:" + ticket.trim();
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            stringRedisTemplate.delete(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            Map<?, ?> map = objectMapper.readValue(payload, Map.class);
            Object rawPlanId = map.get("planId");
            Long planId = rawPlanId instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(rawPlanId));
            return new AiTicketPayload(
                    planId,
                    firstNonBlank(safeMapString(map.get("safetyCode")), safeMapString(map.get("accessKey"))),
                    safeMapString(map.get("profile")));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeMapString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record AiTicketPayload(Long planId, String safetyCode, String profile) {}

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 把方案整理为受控 JSON：仅包含 AI 解读必需的事实字段，
     * 避免传递自由文本/AI 可能用作"幻觉"基础的字段。
     */
    private String buildPlanSummary(PlanHistory plan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("planId", plan.getId());
        root.put("provinceCode", plan.getProvinceCode());
        root.put("volunteerUnitType", plan.getVolunteerUnitType());
        root.put("targetBatch", plan.getTargetBatch());
        root.put("itemCount", plan.getItemCount());
        Map<String, Object> student = new LinkedHashMap<>();
        student.put("totalScore", plan.getTotalScore());
        student.put("provinceRank", plan.getProvinceRank());
        student.put("firstSubject", plan.getFirstSubject());
        student.put("resubjects", parseStringList(plan.getResubjects()));
        student.put("strategyMode", plan.getStrategyMode());
        student.put("decisionPriority", plan.getDecisionPriority());
        student.put("careerGoal", plan.getCareerGoal());
        student.put("tuitionBudget", plan.getTuitionBudget());
        student.put("acceptPrivate", plan.getAcceptPrivate() != null && plan.getAcceptPrivate() == 1);
        student.put("acceptSinoForeign", plan.getAcceptSinoForeign() != null && plan.getAcceptSinoForeign() == 1);
        student.put("preferredMajors", parseStringList(plan.getPreferredMajors()));
        student.put("preferredRegions", parseStringList(plan.getPreferredRegions()));
        root.put("student", student);

        // 全量志愿条目（精简字段，避免泄露内部 ID/算法标签）
        List<VolunteerItem> items = parseItemList(plan.getPlanJson());
        List<Map<String, Object>> simplified = new java.util.ArrayList<>();
        for (VolunteerItem item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", item.getIndex());
            entry.put("gradient", item.getGradient());
            entry.put("universityName", item.getUniversityName());
            entry.put("majorName", item.getMajorName());
            entry.put("province", item.getProvince());
            entry.put("city", item.getCity());
            entry.put("tags", item.getTags());
            entry.put("schoolNature", item.getSchoolNature());
            entry.put("dataSourceType", item.getDataSourceType());
            entry.put("confidenceLabel", item.getConfidenceLabel());
            entry.put("subjectRequirementSource", item.getSubjectRequirementSource());
            entry.put("resubjectRequirement", item.getResubjectRequirement());
            entry.put("referenceYear", item.getReferenceYear());
            entry.put("historyMinScore", item.getHistoryMinScore());
            entry.put("historyMinRank", item.getHistoryMinRank());
            entry.put("rankGap", item.getRankGap());
            entry.put("rankGapRatio", item.getRankGapRatio());
            entry.put("latestPlanCount", item.getLatestPlanCount());
            entry.put("planTrend", item.getPlanTrend());
            entry.put("planRiskNote", item.getPlanRiskNote());
            entry.put("planExpansionIndex", item.getPlanExpansionIndex());
            entry.put("planExpansionLabel", item.getPlanExpansionLabel());
            entry.put("planExpansionNote", item.getPlanExpansionNote());
            entry.put("schoolEnrollmentIndex", item.getSchoolEnrollmentIndex());
            entry.put("schoolEnrollmentLabel", item.getSchoolEnrollmentLabel());
            entry.put("schoolEnrollmentNote", item.getSchoolEnrollmentNote());
            entry.put("recommendationScore", item.getRecommendationScore());
            entry.put("precisionScore", item.getPrecisionScore());
            entry.put("precisionLabel", item.getPrecisionLabel());
            entry.put("precisionNote", item.getPrecisionNote());
            entry.put("chanceScore", item.getChanceScore());
            entry.put("chanceLevel", item.getChanceLevel());
            entry.put("confidenceLevel", item.getConfidenceLevel());
            entry.put("dataConfidence", item.getDataConfidence());
            entry.put("predictedMinRank", item.getPredictedMinRank());
            entry.put("rankDiff", item.getRankDiff());
            entry.put("referenceFitLevel", item.getReferenceFitLevel());
            entry.put("dataConfidenceScore", item.getDataConfidenceScore());
            entry.put("algorithmExplanation", item.getAlgorithmExplanation());
            entry.put("legacySubjectFallback", item.isLegacySubjectFallback());
            entry.put("specialTypeFlag", item.isSpecialTypeFlag());
            entry.put("excludedReason", item.getExcludedReason());
            entry.put("riskLevel", item.getRiskLevel());
            entry.put("trend", item.getTrend());
            entry.put("matchTag", item.getMatchTag());
            entry.put("needsManualReview", item.isNeedsManualReview());
            entry.put("reviewFlags", item.getReviewFlags());
            entry.put("admissionBrochureUrl", item.getAdmissionBrochureUrl());
            entry.put("majorCatalogUrl", item.getMajorCatalogUrl());
            entry.put("tuitionInfoUrl", item.getTuitionInfoUrl());
            simplified.add(entry);
        }
        root.put("items", simplified);

        // 复核清单 + 数据质量 + 监控指标
        root.put("manualReview", parseManualReviewList(plan.getManualReviewJson()));
        root.put("dataQualityWarning", plan.getDataQualityWarning());
        root.put("metrics", parseMetrics(plan.getMetricsJson()));
        Map<String, Object> snapshot = parseMetrics(plan.getRequestSnapshotJson());
        root.put("rankEstimate", snapshot.get("rankEstimate"));
        root.put("gradientRangeSummary", snapshot.get("gradientRangeSummary"));
        VolunteerService.PlanResult advisorPlan = volunteerService.getPlanResultForInternal(plan.getId());
        root.put("advisorAdvice", advisorPlan == null ? null : advisorPlan.getAdvisorAdvice());
        root.put("aiGeneratedNotice", ComplianceConstants.AI_GENERATED_NOTICE);
        root.put("referenceProbabilityNotice", ComplianceConstants.REFERENCE_PROBABILITY_NOTICE);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildAdvisorChatSummary(PlanHistory plan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("planId", plan.getId());
        root.put("provinceCode", plan.getProvinceCode());
        root.put("volunteerUnitType", plan.getVolunteerUnitType());
        root.put("targetBatch", plan.getTargetBatch());
        root.put("itemCount", plan.getItemCount());

        Map<String, Object> student = new LinkedHashMap<>();
        student.put("totalScore", plan.getTotalScore());
        student.put("provinceRank", plan.getProvinceRank());
        student.put("firstSubject", plan.getFirstSubject());
        student.put("resubjects", parseStringList(plan.getResubjects()));
        student.put("strategyMode", plan.getStrategyMode());
        student.put("decisionPriority", plan.getDecisionPriority());
        student.put("careerGoal", plan.getCareerGoal());
        student.put("tuitionBudget", plan.getTuitionBudget());
        student.put("preferredMajors", parseStringList(plan.getPreferredMajors()));
        student.put("preferredRegions", parseStringList(plan.getPreferredRegions()));
        root.put("student", student);

        List<VolunteerItem> items = parseItemList(plan.getPlanJson());
        List<Map<String, Object>> compactItems = new java.util.ArrayList<>();
        for (VolunteerItem item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", item.getIndex());
            entry.put("gradient", item.getGradient());
            entry.put("universityName", item.getUniversityName());
            entry.put("majorName", item.getMajorName());
            entry.put("province", item.getProvince());
            entry.put("city", item.getCity());
            entry.put("schoolNature", item.getSchoolNature());
            entry.put("dataSourceType", item.getDataSourceType());
            entry.put("confidenceLabel", item.getConfidenceLabel());
            entry.put("historyMinScore", item.getHistoryMinScore());
            entry.put("historyMinRank", item.getHistoryMinRank());
            entry.put("rankGap", item.getRankGap());
            entry.put("latestPlanCount", item.getLatestPlanCount());
            entry.put("planTrend", item.getPlanTrend());
            entry.put("planExpansionIndex", item.getPlanExpansionIndex());
            entry.put("planExpansionLabel", item.getPlanExpansionLabel());
            entry.put("schoolEnrollmentIndex", item.getSchoolEnrollmentIndex());
            entry.put("schoolEnrollmentLabel", item.getSchoolEnrollmentLabel());
            entry.put("precisionScore", item.getPrecisionScore());
            entry.put("precisionLabel", item.getPrecisionLabel());
            entry.put("chanceScore", item.getChanceScore());
            entry.put("chanceLevel", item.getChanceLevel());
            entry.put("confidenceLevel", item.getConfidenceLevel());
            entry.put("dataConfidence", item.getDataConfidence());
            entry.put("predictedMinRank", item.getPredictedMinRank());
            entry.put("rankDiff", item.getRankDiff());
            entry.put("riskLevel", item.getRiskLevel());
            entry.put("riskColor", item.getRiskColor());
            entry.put("matchScore", item.getMatchScore());
            entry.put("matchTag", item.getMatchTag());
            entry.put("recommendReason", item.getRecommendReason());
            entry.put("riskReason", item.getRiskReason());
            entry.put("reviewFlags", item.getReviewFlags());
            compactItems.add(entry);
        }
        root.put("items", compactItems);
        root.put("manualReview", parseManualReviewList(plan.getManualReviewJson()));
        root.put("dataQualityWarning", plan.getDataQualityWarning());
        root.put("metrics", parseMetrics(plan.getMetricsJson()));
        Map<String, Object> snapshot = parseMetrics(plan.getRequestSnapshotJson());
        root.put("rankEstimate", snapshot.get("rankEstimate"));
        root.put("gradientRangeSummary", snapshot.get("gradientRangeSummary"));
        root.put("aiGeneratedNotice", ComplianceConstants.AI_GENERATED_NOTICE);
        root.put("referenceProbabilityNotice", ComplianceConstants.REFERENCE_PROBABILITY_NOTICE);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<VolunteerItem> parseItemList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, VolunteerItem.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseManualReviewList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMetrics(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Long tryExtractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long userId = jwtUtil.getUserId(auth.substring(7));
            if (userId != null) return userId;
        }
        return null;
    }

    private String getClientIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        String ip = null;
        if (isTrustedProxy(remoteAddr)) {
            ip = req.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = req.getHeader("X-Real-IP");
            }
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = remoteAddr;
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
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

    private boolean tryAcquireAiAnalysisSlot(String globalKey, String ipKey) {
        try {
            Long global = stringRedisTemplate.opsForValue().increment(globalKey);
            if (global != null && global == 1L) {
                stringRedisTemplate.expire(globalKey, Duration.ofSeconds(aiAnalysisActiveTtlSeconds));
            }
            Long perIp = stringRedisTemplate.opsForValue().increment(ipKey);
            if (perIp != null && perIp == 1L) {
                stringRedisTemplate.expire(ipKey, Duration.ofSeconds(aiAnalysisActiveTtlSeconds));
            }
            if ((global != null && global > aiAnalysisActiveGlobalLimit) || (perIp != null && perIp > aiAnalysisActivePerIpLimit)) {
                decrementCounter(globalKey);
                decrementCounter(ipKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseAiAnalysisSlot(String globalKey, String ipKey, AtomicBoolean released) {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        decrementCounter(globalKey);
        decrementCounter(ipKey);
    }

    private void decrementCounter(String key) {
        try {
            Long value = stringRedisTemplate.opsForValue().decrement(key);
            if (value != null && value <= 0) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * planId 维度的 AI 解读互斥锁：SETNX + TTL。
     * Redis 不可用时 fail-open（兼容现有降级语义），不阻塞用户。
     */
    private boolean tryAcquireAiAnalysisPlanLock(String planLockKey) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                    planLockKey, "1", Duration.ofSeconds(Math.max(aiAnalysisPlanLockTtlSeconds, 30)));
            return ok == null || ok;
        } catch (Exception e) {
            return true;
        }
    }

    private void releaseAiAnalysisPlanLock(String planLockKey) {
        try {
            stringRedisTemplate.delete(planLockKey);
        } catch (Exception ignored) {
        }
    }
}
