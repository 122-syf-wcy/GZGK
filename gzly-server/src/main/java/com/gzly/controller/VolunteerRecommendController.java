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
import com.gzly.service.SafetyCodeIdentityService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.VolunteerService;
import com.gzly.service.recommend.QueryOnlyRecommendEngine;
import com.gzly.service.recommend.RecommendEngineDecision;
import com.gzly.service.recommend.RecommendEngineRouter;
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
    private final BatchSupportService batchSupportService;
    private final RecommendEngineRouter recommendEngineRouter;
    private final QueryOnlyRecommendEngine queryOnlyRecommendEngine;
    private final SafetyCodeRequestResolver safetyCodeRequestResolver;
    private final SafetyCodeService safetyCodeService;
    private final SafetyCodeIdentityService safetyCodeIdentityService;
    private final AdmissionYearService admissionYearService;

    private static final String SAFETY_CODE_REQUIRED_MESSAGE = "请先输入安全码";

    @PostMapping("/recommend")
    public Result<VolunteerService.PlanResult> recommend(@RequestBody VolunteerService.GenerateRequest req,
                                                         HttpServletRequest httpReq) {
        if (req == null) {
            return Result.fail(400, "参数不能为空");
        }
        String rawCode = safetyCodeRequestResolver.resolve(httpReq, req);
        if (rawCode == null || rawCode.isBlank()) {
            throw new BizException(403, SAFETY_CODE_REQUIRED_MESSAGE);
        }
        String safetyCode = safetyCodeService.normalizeSafetyCode(rawCode);
        req.setSafetyCode(safetyCode);
        safetyCodeIdentityService.ensureIdentity(safetyCode);
        normalizePublicRequest(req);
        req.setYear(admissionYearService.requireActiveYearForPublicApi(req.getYear()));
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        PolicyRuleService.PolicyContext policy = policyRuleService.requirePolicy(
                provinceCode, req.getYear(), req.getCandidateType(), req.getBatchCode());
        applyPolicyToRequest(req, policy.getConfig());
        RecommendEngineDecision engineDecision = recommendEngineRouter.resolve(req, policy.getConfig());
        if (!engineDecision.isMainPipelineEngine()) {
            VolunteerService.PlanResult plan = queryOnlyRecommendEngine.generate(req, policy.getConfig(), engineDecision);
            decoratePlan(req, plan, policy, MlPredictionService.ApplyResult.empty(), engineDecision);
            return Result.ok(plan);
        }
        if ("EarlyCParallelMajorEngine".equals(engineDecision.getEngineName())) {
            // EARLY_C 走主推荐链路，但 policyMaxVolunteerCount 必须按 60 平行志愿口径
            req.setPolicyMaxVolunteerCount(engineDecision.getMaxVolunteerCount() > 0
                    ? engineDecision.getMaxVolunteerCount() : 60);
            req.setPolicyBatchName(engineDecision.getBatchName() == null || engineDecision.getBatchName().isBlank()
                    ? "普通类本科提前批C段" : engineDecision.getBatchName());
        }

        VolunteerService.PlanResult plan = provincePolicyService.isProfessionalGroupProvince(provinceCode)
                ? professionalGroupVolunteerService.generate(req, null, getClientIp(httpReq))
                : volunteerService.generate(req, null, getClientIp(httpReq));
        MlPredictionService.ApplyResult mlResult = mlPredictionService.applyPredictions(
                req, plan, policy.getConfig().getMaxVolunteerCount());
        decoratePlan(req, plan, policy, mlResult, engineDecision);
        return Result.ok(plan);
    }

    @GetMapping("/gz/batch-support")
    public Result<BatchSupportService.BatchSupportResponse> gzBatchSupport(@RequestParam(required = false) Integer year) {
        int publicYear = admissionYearService.normalizePublicYear(year);
        return Result.ok(batchSupportService.supportMatrix("GZ", publicYear, true));
    }

    private void decoratePlan(VolunteerService.GenerateRequest req,
                              VolunteerService.PlanResult plan,
                              PolicyRuleService.PolicyContext policy,
                              MlPredictionService.ApplyResult mlResult) {
        decoratePlan(req, plan, policy, mlResult, null);
    }

    private void decoratePlan(VolunteerService.GenerateRequest req,
                              VolunteerService.PlanResult plan,
                              PolicyRuleService.PolicyContext policy,
                              MlPredictionService.ApplyResult mlResult,
                              RecommendEngineDecision engineDecision) {
        if (plan == null || policy == null || policy.getConfig() == null) {
            return;
        }

        List<String> warnings = new ArrayList<>();
        if (policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        if (engineDecision != null && engineDecision.getWarnings() != null) {
            for (String warning : engineDecision.getWarnings()) {
                if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
        }
        BatchSupportService.BatchSupportResponse supportResponse = resolveSupportMatrix(req);
        applyYearContext(plan, supportResponse, warnings);

        Map<String, Object> publicPolicy = new LinkedHashMap<>(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setPolicy(publicPolicy);
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        if (plan.getModelInfo() != null) {
            modelInfo.putAll(plan.getModelInfo());
        }
        if (mlResult != null) {
            modelInfo.putAll(mlResult.toMap());
        }
        if (engineDecision != null) {
            modelInfo.put("engineName", engineDecision.getEngineName());
            modelInfo.put("recommendMode", engineDecision.getRecommendMode());
            modelInfo.put("queryOnly", engineDecision.isQueryOnly());
        }
        applyYearContext(publicPolicy, modelInfo, supportResponse);
        plan.setModelInfo(modelInfo);
        plan.setWarnings(warnings);
        BatchRuleRegistry.find(req.getBatchCode()).ifPresent(rule -> {
            BatchSupportService.BatchSupportItem supportItem = engineDecision == null
                    ? resolveDynamicSupportItem(req, rule, supportResponse)
                    : engineDecision.getSupportItem();
            String supportLevel = supportItem == null || supportItem.getSupportLevel() == null || supportItem.getSupportLevel().isBlank()
                    ? rule.baseSupportLevel().name()
                    : supportItem.getSupportLevel();
            String supportReason = supportItem == null || supportItem.getSupportReason() == null || supportItem.getSupportReason().isBlank()
                    ? rule.supportNote()
                    : supportItem.getSupportReason();
            String recommendMode = rule.recommendMode().name();
            String engineName = rule.engine();
            if (engineDecision != null) {
                supportLevel = engineDecision.getSupportLevel();
                supportReason = engineDecision.getSupportReason();
                recommendMode = engineDecision.getRecommendMode();
                engineName = engineDecision.getEngineName();
            }
            if (rule.mainRankEngine() && isPreOfficialDataResponse(supportResponse)) {
                supportLevel = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
                recommendMode = BatchRuleRegistry.RecommendMode.QUERY_ONLY.name();
                engineName = QueryOnlyRecommendEngine.NAME;
                supportReason = AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
            } else if (rule.mainRankEngine()
                    && isOfficialDataImportedBeforeRetrainResponse(supportResponse)
                    && BatchRuleRegistry.SupportLevel.FULL_RECOMMEND.name().equals(supportLevel)) {
                supportLevel = BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
                supportReason = AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING;
                if (!warnings.contains(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING)) {
                    warnings.add(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
                }
            }
            plan.setSupportLevel(supportLevel);
            plan.setRecommendMode(recommendMode);
            plan.setEngineName(engineName);
            publicPolicy.put("supportLevel", supportLevel);
            publicPolicy.put("recommendMode", recommendMode);
            publicPolicy.put("engineName", engineName);
            publicPolicy.put("supportReason", supportReason);
            modelInfo.put("supportLevel", supportLevel);
            modelInfo.put("recommendMode", recommendMode);
            modelInfo.put("engineName", engineName);
            modelInfo.put("supportReason", supportReason);
            if (BatchRuleRegistry.RecommendMode.QUERY_ONLY.name().equals(recommendMode)) {
                modelInfo.put("queryOnly", true);
                modelInfo.put("visibleMetric", "query_only");
            }
            if (isPreOfficialDataResponse(supportResponse) || plan.getSupportReason() == null || plan.getSupportReason().isBlank()) {
                plan.setSupportReason(supportReason);
            }
            if (supportItem != null && supportItem.getWarnings() != null) {
                for (String warning : supportItem.getWarnings()) {
                    if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                }
            }
        });
        plan.setWarnings(warnings);
    }

    private BatchSupportService.BatchSupportResponse resolveSupportMatrix(VolunteerService.GenerateRequest req) {
        if (req == null) {
            return null;
        }
        try {
            return batchSupportService.supportMatrix(req.getProvinceCode(), req.getYear());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyYearContext(VolunteerService.PlanResult plan,
                                  BatchSupportService.BatchSupportResponse supportResponse,
                                  List<String> warnings) {
        if (plan == null) {
            return;
        }
        if (supportResponse == null) {
            plan.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
            plan.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
            plan.setTargetYear(admissionYearService.getTargetYear());
            plan.setFutureImportYear(admissionYearService.getFutureImportYear());
            plan.setTrainingYears(admissionYearService.resolveTrainingYears());
            plan.setDataSourceYears(admissionYearService.resolveTrainingYears());
            plan.setRecommendationPhase(admissionYearService.getRecommendationPhase());
            plan.setEstimateMode(admissionYearService.isPreOfficialDataPhase(admissionYearService.getRecommendationPhase()));
            plan.setOfficialDataReady(false);
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
        if (isPreOfficialDataResponse(supportResponse) && !warnings.contains(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING)) {
            warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        } else if (isOfficialDataPartialResponse(supportResponse)
                && !warnings.contains(AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING)) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING);
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

    private boolean isPreOfficialDataResponse(BatchSupportService.BatchSupportResponse response) {
        return response != null
                && (response.isEstimateMode()
                || AdmissionYearService.PHASE_PRE_OFFICIAL_DATA.equals(response.getRecommendationPhase()));
    }

    private boolean isOfficialDataPartialResponse(BatchSupportService.BatchSupportResponse response) {
        return response != null
                && AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL.equals(response.getRecommendationPhase());
    }

    private boolean isOfficialDataImportedBeforeRetrainResponse(BatchSupportService.BatchSupportResponse response) {
        if (response == null || !response.isOfficialDataReady()) {
            return false;
        }
        BatchSupportService.DataReadiness readiness = response.getDataReadiness();
        return AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED.equals(response.getRecommendationPhase())
                || (readiness != null
                && AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED.equals(readiness.getRecommendationPhase())
                && !readiness.isMlTrainingReady());
    }

    private BatchSupportService.BatchSupportItem resolveDynamicSupportItem(VolunteerService.GenerateRequest req,
                                                                           BatchRuleRegistry.BatchRule rule) {
        return resolveDynamicSupportItem(req, rule, resolveSupportMatrix(req));
    }

    private BatchSupportService.BatchSupportItem resolveDynamicSupportItem(VolunteerService.GenerateRequest req,
                                                                           BatchRuleRegistry.BatchRule rule,
                                                                           BatchSupportService.BatchSupportResponse response) {
        if (req == null || rule == null || !rule.mainRankEngine()) {
            return null;
        }
        try {
            if (response == null || response.getItems() == null) {
                return null;
            }
            return response.getItems().stream()
                    .filter(item -> rule.batchCode().equals(item.getBatchCode()))
                    .filter(item -> BatchRuleRegistry.candidateTypeMatches(rule.candidateType(), item.getCandidateType()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void normalizePublicRequest(VolunteerService.GenerateRequest req) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        req.setProvinceCode(provinceCode);
        req.setBatchCode(policyRuleService.normalizeBatchCode(req.getBatchCode()));
        req.setCandidateType(BatchRuleRegistry.normalizeCandidateType(req.getCandidateType()));
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
