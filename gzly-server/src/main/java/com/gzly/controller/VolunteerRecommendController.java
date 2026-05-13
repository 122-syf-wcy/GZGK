package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import com.gzly.service.DataYearReadinessService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.VolunteerService;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class VolunteerRecommendController {

    private final VolunteerService volunteerService;
    private final ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    private final ProvincePolicyService provincePolicyService;
    private final PolicyRuleService policyRuleService;
    private final MlPredictionService mlPredictionService;
    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;
    private final JwtUtil jwtUtil;

    @PostMapping("/recommend")
    public Result<VolunteerService.PlanResult> recommend(@RequestBody VolunteerService.GenerateRequest req,
                                                         HttpServletRequest httpReq) {
        if (req == null) {
            return Result.fail(400, "参数不能为空");
        }
        Long userId = tryExtractUserId(httpReq);
        normalizePublicRequest(req);
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());

        // PRE_OFFICIAL_DATA 闸门：提前批/艺术/体育/专项类续继仅返回 QUERY_ONLY，不走推荐生成/ML 调用。
        BatchRuleRegistry.BatchRule rule = BatchRuleRegistry.find(req.getBatchCode())
                .orElse(BatchRuleRegistry.require(BatchRuleRegistry.DEFAULT_BATCH_CODE));
        int resolvedYear = req.getYear() != null && req.getYear() > 0
                ? req.getYear()
                : admissionYearService.getTargetYear();
        BatchSupportService.DataReadiness readiness = dataYearReadinessService.getOrDefaultReadiness(provinceCode, resolvedYear);
        boolean officialReady = admissionYearService.isOfficialDataReady(readiness);
        boolean isOrdinary = rule.category() == BatchRuleRegistry.CandidateCategory.ORDINARY
                && rule.mainRankEngine();
        boolean preOfficial = admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase());
        if (resolvedYear == admissionYearService.getTargetYear() && !officialReady && !isOrdinary) {
            return Result.ok(buildQueryOnlyResponse(rule, provinceCode, resolvedYear, readiness));
        }

        PolicyRuleService.PolicyContext policy = policyRuleService.requirePolicy(
                provinceCode, req.getYear(), req.getCandidateType(), req.getBatchCode());
        applyPolicyToRequest(req, policy.getConfig());

        VolunteerService.PlanResult plan = provincePolicyService.isProfessionalGroupProvince(provinceCode)
                ? professionalGroupVolunteerService.generate(req, userId, getClientIp(httpReq))
                : volunteerService.generate(req, userId, getClientIp(httpReq));
        MlPredictionService.ApplyResult mlResult = mlPredictionService.applyPredictions(
                req, plan, policy.getConfig().getMaxVolunteerCount());

        List<String> warnings = new ArrayList<>();
        if (policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        String supportLevel = resolveSupportLevel(rule, readiness, resolvedYear, officialReady, preOfficial);
        if (BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name().equals(supportLevel)) {
            warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
        } else if (BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        }

        plan.setPolicy(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setModelInfo(mlResult.toMap());
        plan.setWarnings(warnings);
        applyRecommendationMeta(plan, readiness, supportLevel, resolvedYear);
        return Result.ok(plan);
    }

    /**
     * 非普通本/专科批次在 PRE_OFFICIAL_DATA 阶段的响应：不生成志愿列表、不调用 ML，仅返回 QUERY_ONLY 说明。
     */
    private VolunteerService.PlanResult buildQueryOnlyResponse(BatchRuleRegistry.BatchRule rule,
                                                              String provinceCode,
                                                              int resolvedYear,
                                                              BatchSupportService.DataReadiness readiness) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode(provinceCode);
        plan.setTargetBatch(rule.batchName());
        plan.setTargetCount(0);
        plan.setItems(List.of());
        List<String> warnings = new ArrayList<>();
        if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING);
        } else {
            warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        }
        warnings.add(rule.supportNote());
        plan.setWarnings(warnings);
        plan.setSupportLevel(BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        applyRecommendationMeta(plan, readiness, BatchRuleRegistry.SupportLevel.QUERY_ONLY.name(), resolvedYear);
        return plan;
    }

    /**
     * 根据 readiness + 批次规则解析当前 supportLevel。
     * - PRE_OFFICIAL_DATA + ORDINARY + 历史数据就绪 → ESTIMATE_RECOMMEND
     * - OFFICIAL_DATA_IMPORTED 但未重训 → TRIAL_RECOMMEND
     * - MODEL_RETRAINED + 所有门禁通过 → FULL_RECOMMEND
     * 其他 → QUERY_ONLY。
     */
    private String resolveSupportLevel(BatchRuleRegistry.BatchRule rule,
                                       BatchSupportService.DataReadiness readiness,
                                       int resolvedYear,
                                       boolean officialReady,
                                       boolean preOfficial) {
        boolean targetYear = resolvedYear == admissionYearService.getTargetYear();
        boolean isOrdinaryMain = rule.category() == BatchRuleRegistry.CandidateCategory.ORDINARY
                && rule.mainRankEngine();
        if (!isOrdinaryMain) {
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (targetYear && !officialReady) {
            if (preOfficial && readiness != null && readiness.isHistoricalTrainingReady()) {
                return BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name();
            }
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (targetYear && !readiness.isMlTrainingReady()) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        if (targetYear && !admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        return rule.baseSupportLevel().name();
    }

    /**
     * 统一为返回的 PlanResult 写入推荐阶段元数据，避免前端需要额外拉取。
     */
    private void applyRecommendationMeta(VolunteerService.PlanResult plan,
                                         BatchSupportService.DataReadiness readiness,
                                         String supportLevel,
                                         int resolvedYear) {
        plan.setRecommendationPhase(readiness == null
                ? AdmissionYearService.PHASE_PRE_OFFICIAL_DATA
                : readiness.getRecommendationPhase());
        plan.setSupportLevel(supportLevel);
        boolean officialReady = admissionYearService.isOfficialDataReady(readiness);
        plan.setOfficialDataReady(officialReady);
        plan.setModelRetrained(readiness != null
                && readiness.isMlTrainingReady()
                && admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase()));
        boolean isEstimate = BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name().equals(supportLevel);
        plan.setEstimateMode(isEstimate || (resolvedYear == admissionYearService.getTargetYear() && !officialReady));
        plan.setTargetYear(resolvedYear);
        plan.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        plan.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        List<Integer> dataYears = admissionYearService.resolveTrainingYears();
        if (resolvedYear == admissionYearService.getTargetYear()
                && readiness != null
                && readiness.isMlTrainingReady()
                && admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                && !dataYears.contains(resolvedYear)) {
            dataYears = new ArrayList<>(dataYears);
            dataYears.add(resolvedYear);
        }
        plan.setDataSourceYears(dataYears);
    }

    private void normalizePublicRequest(VolunteerService.GenerateRequest req) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        req.setProvinceCode(provinceCode);
        req.setBatchCode(policyRuleService.normalizeBatchCode(req.getBatchCode()));
        if (req.getScore() != null && req.getScore() > 0) {
            req.setTotalScore(req.getScore());
        }
        if (req.getRank() != null && req.getRank() > 0) {
            req.setProvinceRank(req.getRank());
        }
        if ((req.getFirstSubject() == null || req.getFirstSubject().isBlank()) && req.getSubjectType() != null) {
            String subject = req.getSubjectType().replace("类", "").trim();
            req.setFirstSubject(subject);
        }
        if ((req.getResubjects() == null || req.getResubjects().isEmpty()) && req.getSelectedSubjects() != null) {
            String first = req.getFirstSubject() == null ? "" : req.getFirstSubject().trim();
            req.setResubjects(req.getSelectedSubjects().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.equals(first) && !(s + "类").equals(req.getSubjectType()))
                    .limit(2)
                    .toList());
        }
        if (req.getPreferredRegions() == null || req.getPreferredRegions().isEmpty()) {
            List<String> regions = new ArrayList<>();
            if (req.getPreferredCities() != null) {
                regions.addAll(req.getPreferredCities());
            }
            if (req.getPreferredProvinces() != null) {
                regions.addAll(req.getPreferredProvinces());
            }
            req.setPreferredRegions(regions);
        }
        if (req.getAcceptPrivate() == null && req.getAcceptPrivateSchool() != null) {
            req.setAcceptPrivate(req.getAcceptPrivateSchool());
        }
        if (req.getAcceptSinoForeign() == null && req.getAcceptChineseForeignCoop() != null) {
            req.setAcceptSinoForeign(req.getAcceptChineseForeignCoop());
        }
        if (req.getStrategyMode() == null || req.getStrategyMode().isBlank()) {
            req.setStrategyMode(normalizeRiskPreference(req.getRiskPreference()));
        }
    }

    private String normalizeRiskPreference(String riskPreference) {
        if (riskPreference == null || riskPreference.isBlank()) {
            return "均衡型";
        }
        String value = riskPreference.trim().toLowerCase();
        return switch (value) {
            case "conservative", "保守", "保守型" -> "保守型";
            case "aggressive", "激进", "冲刺", "冲刺型" -> "冲刺型";
            default -> "均衡型";
        };
    }

    private void applyPolicyToRequest(VolunteerService.GenerateRequest req,
                                      com.gzly.entity.PolicyRuleConfig config) {
        if (req == null || config == null) {
            return;
        }
        req.setPolicyMaxVolunteerCount(config.getMaxVolunteerCount());
        req.setPolicyBatchName(config.getBatchName());
        req.setPolicyVolunteerUnitLabel(config.getVolunteerMode());
        req.setPolicyVolunteerUnitType(ProvincePolicyService.UNIT_MAJOR_96);
    }

    private Long tryExtractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return jwtUtil.getUserId(auth.substring(7));
        }
        return null;
    }

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = req.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
