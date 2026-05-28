package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.DataReadinessService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvinceAlgorithmPolicyService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.VolunteerService;
import com.gzly.entity.PlanHistory;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class VolunteerRecommendController {

    private static final int PUBLIC_RECOMMEND_YEAR = 2026;

    private final VolunteerService volunteerService;
    private final ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    private final ProvincePolicyService provincePolicyService;
    private final ProvinceAlgorithmPolicyService provinceAlgorithmPolicyService;
    private final PolicyRuleService policyRuleService;
    private final MlPredictionService mlPredictionService;
    private final DataReadinessService dataReadinessService;
    private final JwtUtil jwtUtil;
    private final PlanHistoryMapper planHistoryMapper;

    @PostMapping("/recommend")
    public Result<VolunteerService.PlanResult> recommend(@RequestBody VolunteerService.GenerateRequest req,
                                                         HttpServletRequest httpReq) {
        if (req == null) {
            return Result.fail(400, "参数不能为空");
        }
        Long userId = tryExtractUserId(httpReq);
        normalizePublicRequest(req);
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        int year = req.getYear() == null || req.getYear() <= 0 ? PUBLIC_RECOMMEND_YEAR : req.getYear();
        req.setYear(year);
        PolicyRuleService.PolicyContext policy = policyRuleService.requirePolicy(
                provinceCode, year, req.getCandidateType(), req.getBatchCode());
        ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm = provinceAlgorithmPolicyService.resolve(provinceCode, policy.getConfig());
        DataReadinessService.Readiness readiness = dataReadinessService.get(provinceCode, year);
        boolean fullRecommendReady = dataReadinessService.isFullRecommendReady(provinceCode, year);
        boolean estimateRecommendReady = DataReadinessService.PRE_OFFICIAL_DATA.equalsIgnoreCase(readiness.recommendationPhase);
        if (!fullRecommendReady && !estimateRecommendReady) {
            throw new BizException(409, "%s%s当前年度数据门禁未通过：仅开放批次策略和数据缺口展示，未开放志愿生成。"
                    .formatted(provinceCode, policy.getConfig().getBatchName()));
        }
        applyPolicyToRequest(req, policy.getConfig());

        VolunteerService.PlanResult plan = !algorithm.isGeneratorReady()
                ? buildQueryOnlyPlan(req, userId, getClientIp(httpReq), policy.getConfig(), algorithm, readiness)
                : provincePolicyService.isProfessionalGroupProvince(provinceCode)
                ? professionalGroupVolunteerService.generate(req, userId, getClientIp(httpReq))
                : volunteerService.generate(req, userId, getClientIp(httpReq));
        MlPredictionService.ApplyResult mlResult = mlPredictionService.applyPredictions(
                req, plan, policyMaxVolunteerCount(policy.getConfig()));

        List<String> warnings = new ArrayList<>();
        if (policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        if (!fullRecommendReady) {
            warnings.add("当前为PRE_OFFICIAL_DATA历史估算模式：仅基于已核验历史数据生成草稿，不代表2026官方招生计划、投档线或录取承诺。");
        }

        plan.setPolicy(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setModelInfo(mlResult.toMap());
        plan.setWarnings(warnings);
        decoratePublicPlan(plan, policy.getConfig(), algorithm, readiness);
        return Result.ok(plan);
    }

    private VolunteerService.PlanResult buildQueryOnlyPlan(VolunteerService.GenerateRequest req,
                                                           Long userId,
                                                           String clientIp,
                                                           com.gzly.entity.PolicyRuleConfig config,
                                                           ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm,
                                                           DataReadinessService.Readiness readiness) {
        ProvincePolicyService.ProvincePolicy province = provincePolicyService.getPolicy(config.getProvince());
        String supportReason = queryOnlySupportReason(province, config, algorithm);
        PlanHistory history = new PlanHistory();
        history.setUserId(userId != null ? userId : 0L);
        history.setClientIp(clientIp);
        history.setProvinceCode(province.getProvinceCode());
        history.setVolunteerUnitType(province.getVolunteerUnitType());
        history.setTargetBatch(config.getBatchName());
        history.setAgreedDisclaimer(Boolean.TRUE.equals(req.getAgreedDisclaimer()) ? 1 : 0);
        history.setDisclaimerVersion(req.getDisclaimerVersion());
        history.setDisclaimerConfirmedAt(LocalDateTime.now());
        history.setTotalScore(req.getTotalScore());
        history.setProvinceRank(req.getProvinceRank());
        history.setFirstSubject(req.getFirstSubject());
        history.setResubjects(toJsonList(req.getResubjects()));
        history.setPreferredMajors(toJsonList(req.getPreferredMajors()));
        history.setPreferredRegions(toJsonList(req.getPreferredRegions()));
        history.setStrategyMode(defaultText(req.getStrategyMode(), "均衡型"));
        history.setDecisionPriority(defaultText(req.getDecisionPriority(), "专业优先"));
        history.setCareerGoal(defaultText(req.getCareerGoal(), "就业优先"));
        history.setTuitionBudget(defaultText(req.getTuitionBudget(), "均衡预算"));
        history.setAcceptPrivate(Boolean.FALSE.equals(req.getAcceptPrivate()) ? 0 : 1);
        history.setAcceptSinoForeign(Boolean.TRUE.equals(req.getAcceptSinoForeign()) ? 1 : 0);
        history.setPlanJson("[]");
        history.setItemCount(0);
        history.setManualReviewJson("[]");
        history.setDataQualityWarning(supportReason);
        history.setMetricsJson("{}");
        history.setRequestSnapshotJson(toJson(queryOnlySnapshot(req, config, algorithm, readiness, supportReason)));
        history.setCreatedAt(LocalDateTime.now());
        planHistoryMapper.insert(history);

        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(history.getId());
        plan.setProvinceCode(province.getProvinceCode());
        plan.setResponseProvince(province.getProvinceCode());
        plan.setProvinceName(province.getProvinceName());
        plan.setVolunteerUnitType(province.getVolunteerUnitType());
        plan.setVolunteerUnitLabel(province.getVolunteerUnitLabel());
        plan.setTargetBatch(config.getBatchName());
        plan.setTargetCount(0);
        plan.setTotalScore(req.getTotalScore());
        plan.setProvinceRank(req.getProvinceRank());
        plan.setFirstSubject(req.getFirstSubject());
        plan.setResubjects(req.getResubjects() == null ? List.of() : req.getResubjects());
        plan.setPreferredMajors(req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        plan.setPreferredRegions(req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        plan.setStrategyMode(history.getStrategyMode());
        plan.setDecisionPriority(history.getDecisionPriority());
        plan.setCareerGoal(history.getCareerGoal());
        plan.setTuitionBudget(history.getTuitionBudget());
        plan.setAcceptPrivate(history.getAcceptPrivate() == 1);
        plan.setAcceptSinoForeign(history.getAcceptSinoForeign() == 1);
        plan.setAccessKey(volunteerService.buildPlanAccessKey(history.getId()));
        plan.setItems(List.of());
        plan.setManualReviewItems(List.of());
        plan.setCreatedAt(history.getCreatedAt().toString());
        plan.setDataQualityWarning(supportReason);
        plan.setSupportReason(supportReason);
        plan.setDiagnosis(queryOnlyDiagnosis(province, config, algorithm));
        return plan;
    }

    private void normalizePublicRequest(VolunteerService.GenerateRequest req) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        req.setProvinceCode(provinceCode);
        req.setCandidateType(policyRuleService.normalizeCandidateType(req.getCandidateType()));
        req.setBatchCode(policyRuleService.normalizeBatchCode(provinceCode, req.getBatchCode()));
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
        req.setPolicyVolunteerUnitType(provincePolicyService.getPolicy(config.getProvince()).getVolunteerUnitType());
    }

    private void decoratePublicPlan(VolunteerService.PlanResult plan,
                                    com.gzly.entity.PolicyRuleConfig config,
                                    ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm,
                                    DataReadinessService.Readiness readiness) {
        if (plan == null) return;
        plan.setTargetYear(PUBLIC_RECOMMEND_YEAR);
        plan.setDataSourceYears(List.of(2024, 2025));
        plan.setRecommendationPhase(readiness.recommendationPhase);
        plan.setEstimateMode(true);
        plan.setSupportLevel(algorithm.isGeneratorReady() ? "ESTIMATE_RECOMMEND" : "QUERY_ONLY");
        plan.setRecommendMode(algorithm.isGeneratorReady() ? algorithm.getRecommendMode() : "QUERY_ONLY");
        plan.setEngineName(algorithm.getEngineName());
        plan.setResponseProvince(config.getProvince());
        if (plan.getSupportReason() == null || plan.getSupportReason().isBlank()) {
            plan.setSupportReason(algorithm.isGeneratorReady()
                    ? config.getBatchName() + "当前为历史估算方案。"
                    : queryOnlySupportReason(provincePolicyService.getPolicy(config.getProvince()), config, algorithm));
        }
        if (plan.getDiagnosis() == null) {
            plan.setDiagnosis(queryOnlyDiagnosis(provincePolicyService.getPolicy(config.getProvince()), config, algorithm));
        }
    }

    private int policyMaxVolunteerCount(com.gzly.entity.PolicyRuleConfig config) {
        Integer value = config == null ? null : config.getMaxVolunteerCount();
        return value == null || value < 0 ? 0 : value;
    }

    private String queryOnlySupportReason(ProvincePolicyService.ProvincePolicy province,
                                          com.gzly.entity.PolicyRuleConfig config,
                                          ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm) {
        return province.getProvinceName() + config.getBatchName()
                + "当前仅生成策略建议和数据缺口方案；官方结构化计划、专业代码、组内专业、分数位次或核心来源补齐前，暂不生成院校志愿清单。"
                + " " + algorithm.getReason();
    }

    private Map<String, Object> queryOnlyDiagnosis(ProvincePolicyService.ProvincePolicy province,
                                                   com.gzly.entity.PolicyRuleConfig config,
                                                   ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("supportReason", queryOnlySupportReason(province, config, algorithm));
        diagnosis.put("missingData", provinceMissingData(province.getProvinceCode()));
        diagnosis.put("futureProfessionalTableFields", List.of("院校代码", "专业组代码", "专业代码", "专业名称", "2025/2024/2023分数位次", "学制", "学费", "招生人数", "官方来源"));
        diagnosis.put("noCollegeListReason", "QUERY_ONLY 阶段不生成假院校清单。");
        return diagnosis;
    }

    private List<String> provinceMissingData(String provinceCode) {
        return switch (provinceCode) {
            case ProvincePolicyService.GX -> List.of("gx_undergraduate_group_plan_and_major_catalog", "gx_professional_major_code_source", "official_2026_admission_plan");
            case ProvincePolicyService.HI -> List.of("hi_score_rank_3plus3_official_source", "hi_required_subjects_major_catalog", "official_2026_admission_plan");
            case ProvincePolicyService.YN -> List.of("yn_2025_new_gaokao_ocr_reviewed_source", "yn_group_plan_major_catalog", "official_2026_admission_plan");
            case ProvincePolicyService.HA -> List.of("ha_official_core_gaokao_source", "ha_group_plan_major_catalog", "official_2026_admission_plan");
            default -> List.of("official_2026_admission_plan", "official_2026_score_or_rank");
        };
    }

    private Map<String, Object> queryOnlySnapshot(VolunteerService.GenerateRequest req,
                                                  com.gzly.entity.PolicyRuleConfig config,
                                                  ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithm,
                                                  DataReadinessService.Readiness readiness,
                                                  String supportReason) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provinceCode", config.getProvince());
        snapshot.put("volunteerUnitType", provincePolicyService.getPolicy(config.getProvince()).getVolunteerUnitType());
        snapshot.put("targetBatch", config.getBatchName());
        snapshot.put("targetCount", 0);
        snapshot.put("targetYear", PUBLIC_RECOMMEND_YEAR);
        snapshot.put("dataSourceYears", List.of(2024, 2025));
        snapshot.put("recommendationPhase", readiness.recommendationPhase);
        snapshot.put("supportLevel", "QUERY_ONLY");
        snapshot.put("recommendMode", "QUERY_ONLY");
        snapshot.put("engineName", algorithm.getEngineName());
        snapshot.put("supportReason", supportReason);
        snapshot.put("diagnosis", queryOnlyDiagnosis(provincePolicyService.getPolicy(config.getProvince()), config, algorithm));
        snapshot.put("totalScore", req.getTotalScore());
        snapshot.put("provinceRank", req.getProvinceRank());
        snapshot.put("firstSubject", req.getFirstSubject());
        snapshot.put("resubjects", req.getResubjects() == null ? List.of() : req.getResubjects());
        snapshot.put("preferredMajors", req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        snapshot.put("preferredRegions", req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String toJsonList(List<String> value) {
        return toJson(value == null ? List.of() : value);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
