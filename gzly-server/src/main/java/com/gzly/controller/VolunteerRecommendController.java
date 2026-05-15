package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final JwtUtil jwtUtil;
    private final BatchSupportService batchSupportService;
    private final AdmissionYearService admissionYearService;

    @PostMapping("/recommend")
    public Result<VolunteerService.PlanResult> recommend(@RequestBody VolunteerService.GenerateRequest req,
                                                         HttpServletRequest httpReq) {
        if (req == null) {
            return Result.fail(400, "参数不能为空");
        }
        Long userId = tryExtractUserId(httpReq);
        normalizePublicRequest(req);
        req.setYear(admissionYearService.requireActiveYearForPublicApi(req.getYear()));
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        PolicyRuleService.PolicyContext policy = policyRuleService.requirePolicy(
                provinceCode, req.getYear(), req.getCandidateType(), req.getBatchCode());
        applyPolicyToRequest(req, policy.getConfig());
        BatchSupportService.BatchSupportResponse supportResponse =
                batchSupportService.supportMatrix(provinceCode, req.getYear(), true);
        if (!supportResponse.isOfficialDataReady()
                && AdmissionYearService.PHASE_PRE_OFFICIAL_DATA.equals(supportResponse.getRecommendationPhase())) {
            return Result.ok(queryOnlyPreOfficialPlan(req, policy, supportResponse));
        }

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

        plan.setPolicy(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setModelInfo(mlResult.toMap());
        plan.setWarnings(warnings);
        applyYearContext(plan, supportResponse, warnings);
        return Result.ok(plan);
    }

    @GetMapping("/gz/batch-support")
    public Result<BatchSupportService.BatchSupportResponse> gzBatchSupport(@RequestParam(required = false) Integer year) {
        int publicYear = admissionYearService.normalizePublicYear(year);
        return Result.ok(batchSupportService.supportMatrix("GZ", publicYear, true));
    }

    private VolunteerService.PlanResult queryOnlyPreOfficialPlan(VolunteerService.GenerateRequest req,
                                                                 PolicyRuleService.PolicyContext policy,
                                                                 BatchSupportService.BatchSupportResponse supportResponse) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(0L);
        plan.setProvinceCode(req.getProvinceCode());
        plan.setTargetBatch(policy.getConfig().getBatchName());
        plan.setTargetCount(policy.getConfig().getMaxVolunteerCount());
        plan.setTotalScore(req.getTotalScore());
        plan.setProvinceRank(req.getProvinceRank());
        plan.setFirstSubject(req.getFirstSubject());
        plan.setResubjects(req.getResubjects());
        plan.setPreferredMajors(req.getPreferredMajors());
        plan.setPreferredRegions(req.getPreferredRegions());
        plan.setStrategyMode(req.getStrategyMode());
        plan.setItems(List.of());
        plan.setDataQualityWarning(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);

        List<String> warnings = new ArrayList<>();
        if (policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        plan.setWarnings(warnings);

        Map<String, Object> publicPolicy = new LinkedHashMap<>(policyRuleService.toPublicPolicy(policy.getConfig()));
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("queryOnly", true);
        modelInfo.put("visibleMetric", "query_only");
        modelInfo.put("supportLevel", BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        modelInfo.put("recommendMode", BatchRuleRegistry.RecommendMode.QUERY_ONLY.name());
        modelInfo.put("engineName", "QueryOnlyRecommendEngine");
        applyYearContext(publicPolicy, modelInfo, supportResponse);
        publicPolicy.put("supportLevel", BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        publicPolicy.put("recommendMode", BatchRuleRegistry.RecommendMode.QUERY_ONLY.name());
        publicPolicy.put("engineName", "QueryOnlyRecommendEngine");
        publicPolicy.put("supportReason", AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        plan.setPolicy(publicPolicy);
        plan.setModelInfo(modelInfo);
        plan.setSupportLevel(BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        plan.setRecommendMode(BatchRuleRegistry.RecommendMode.QUERY_ONLY.name());
        plan.setEngineName("QueryOnlyRecommendEngine");
        plan.setSupportReason(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        applyYearContext(plan, supportResponse, warnings);
        return plan;
    }

    private void applyYearContext(VolunteerService.PlanResult plan,
                                  BatchSupportService.BatchSupportResponse supportResponse,
                                  List<String> warnings) {
        if (plan == null || supportResponse == null) {
            return;
        }
        plan.setActiveAdmissionYear(supportResponse.getActiveAdmissionYear());
        plan.setLatestOfficialDataYear(supportResponse.getLatestOfficialDataYear());
        plan.setTargetYear(supportResponse.getTargetYear());
        plan.setFutureImportYear(supportResponse.getFutureImportYear());
        plan.setTrainingYears(supportResponse.getTrainingYears());
        plan.setDataSourceYears(supportResponse.getDataSourceYears());
        plan.setRecommendationPhase(supportResponse.getRecommendationPhase());
        plan.setEstimateMode(supportResponse.isEstimateMode());
        plan.setOfficialDataReady(supportResponse.isOfficialDataReady());
        plan.setDataReadiness(supportResponse.getDataReadiness());
        if (warnings != null
                && supportResponse.isEstimateMode()
                && !warnings.contains(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING)) {
            warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
            plan.setWarnings(warnings);
        }
    }

    private void applyYearContext(Map<String, Object> publicPolicy,
                                  Map<String, Object> modelInfo,
                                  BatchSupportService.BatchSupportResponse supportResponse) {
        if (supportResponse == null) {
            return;
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("activeAdmissionYear", supportResponse.getActiveAdmissionYear());
        target.put("latestOfficialDataYear", supportResponse.getLatestOfficialDataYear());
        target.put("trainingYears", supportResponse.getTrainingYears());
        target.put("targetYear", supportResponse.getTargetYear());
        target.put("futureImportYear", supportResponse.getFutureImportYear());
        target.put("dataSourceYears", supportResponse.getDataSourceYears());
        target.put("recommendationPhase", supportResponse.getRecommendationPhase());
        target.put("estimateMode", supportResponse.isEstimateMode());
        target.put("officialDataReady", supportResponse.isOfficialDataReady());
        target.put("dataReadiness", supportResponse.getDataReadiness());
        publicPolicy.putAll(target);
        modelInfo.putAll(target);
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
