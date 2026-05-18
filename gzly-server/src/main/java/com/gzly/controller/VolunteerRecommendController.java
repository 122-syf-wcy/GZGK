package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.AnhuiBatchListingService;
import com.gzly.service.AnhuiBatchRuleRegistry;
import com.gzly.service.AnhuiBatchSupportService;
import com.gzly.service.AnhuiCompositeScoreCalculator;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.BatchSupportService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.SafetyCodeIdentityService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.SichuanBatchListingService;
import com.gzly.service.SichuanBatchRuleRegistry;
import com.gzly.service.SichuanBatchSupportService;
import com.gzly.service.SichuanCompositeScoreCalculator;
import com.gzly.service.VolunteerService;
import com.gzly.service.recommend.ProvinceBatchEngineMatrix;
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
    private final SichuanBatchSupportService sichuanBatchSupportService;
    private final SichuanBatchListingService sichuanBatchListingService;
    private final SichuanCompositeScoreCalculator sichuanCompositeScoreCalculator;
    private final AnhuiBatchSupportService anhuiBatchSupportService;
    private final AnhuiBatchListingService anhuiBatchListingService;
    private final AnhuiCompositeScoreCalculator anhuiCompositeScoreCalculator;
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
        applyPolicyToRequest(req, policy.getConfig(), provinceCode);

        if (ProvincePolicyService.AH.equals(provinceCode)) {
            return Result.ok(recommendForAnhuiProvince(req, policy, provinceCode, httpReq));
        }
        if (provincePolicyService.isProfessionalGroupProvince(provinceCode)) {
            return Result.ok(recommendForProfessionalGroupProvince(req, policy, provinceCode, httpReq));
        }

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

        VolunteerService.PlanResult plan = volunteerService.generate(req, null, getClientIp(httpReq));
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

    /**
     * 四川 18 批次支持矩阵（含 SC_BENKE_B 主流程 + 提前批 / 艺术 / 体育 / 专项 QUERY_ONLY 兜底）。
     * 前端 VolunteerForm 在 provinceCode=SC 时调用，用于渲染批次选择器与状态徽标。
     */
    @GetMapping("/sc/batch-support")
    public Result<BatchSupportService.BatchSupportResponse> scBatchSupport(@RequestParam(required = false) Integer year) {
        int publicYear = admissionYearService.normalizePublicYear(year);
        return Result.ok(sichuanBatchSupportService.supportMatrix(ProvincePolicyService.SC, publicYear, true));
    }

    /**
     * 安徽 14 批次支持矩阵（含 AH_BENKE / AH_ZHUANKE 主流程 + 提前批 / 艺术 / 体育 / 专项 QUERY_ONLY 兜底）。
     * 前端 VolunteerForm 在 provinceCode=AH 时调用，用于渲染批次选择器与状态徽标。
     */
    @GetMapping("/ah/batch-support")
    public Result<BatchSupportService.BatchSupportResponse> ahBatchSupport(@RequestParam(required = false) Integer year) {
        int publicYear = admissionYearService.normalizePublicYear(year);
        return Result.ok(anhuiBatchSupportService.supportMatrix(ProvincePolicyService.AH, publicYear, true));
    }

    /**
     * 安徽艺术 / 体育综合分实时计算（皖招委〔2024〕11 号 + 2025 体育文化控线公告）。
     *
     * <p>艺术类两档公式：</p>
     * <ul>
     *   <li>综合分1（音乐 / 舞蹈 / 表（导）演 / 美术与设计 / 书法）：文化 × 50% + 统考 × 2.5 × 50%</li>
     *   <li>综合分2（播音与主持）：文化 × 70% + 统考 × 2.5 × 30%</li>
     * </ul>
     *
     * <p>体育类公式：综合 = 1.2 × 专业 + 0.8 × [60 + 40 × (文化 - 本科文化控线) ÷ (750 - 控线)]，
     * 默认按 2025 安徽本科文化控线（物理 300 / 历史 310）。</p>
     */
    @GetMapping("/ah/composite-score")
    public Result<Map<String, Object>> ahCompositeScore(
            @RequestParam(required = false, defaultValue = "艺术类") String candidateType,
            @RequestParam(required = false) String artCategory,
            @RequestParam(required = false, defaultValue = "物理") String firstSubject,
            @RequestParam int cultureScore,
            @RequestParam int professionalScore) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidateType", candidateType);
        payload.put("artCategory", artCategory == null ? "" : artCategory);
        payload.put("firstSubject", firstSubject == null ? "" : firstSubject);
        payload.put("cultureScore", cultureScore);
        payload.put("professionalScore", professionalScore);
        payload.put("supportedArtCategories", anhuiCompositeScoreCalculator.listArtCategories());

        anhuiCompositeScoreCalculator.calculate(candidateType, cultureScore, professionalScore, artCategory, firstSubject)
                .ifPresentOrElse(result -> {
                    payload.put("success", true);
                    payload.put("score", result.getScore());
                    payload.put("category", result.getCategory());
                    payload.put("selectedCategory", result.getSelectedCategory());
                    payload.put("cultureRatio", result.getCultureRatio());
                    payload.put("professionalRatio", result.getProfessionalRatio());
                    payload.put("cultureWeighted", result.getCultureWeighted());
                    payload.put("professionalWeighted", result.getProfessionalWeighted());
                    payload.put("professionalScale", result.getProfessionalScale());
                    payload.put("cultureBenkeLine", result.getCultureBenkeLine());
                    payload.put("formula", result.getFormula());
                }, () -> {
                    payload.put("success", false);
                    payload.put("score", 0.0);
                    payload.put("formula", "考生类别 + 统考类别无法识别，无法计算综合分");
                });
        return Result.ok(payload);
    }

    /**
     * 四川艺术 / 体育综合分实时计算（前端 VolunteerForm 在用户输入文化分 + 统考分时调用，
     * 用于在表单旁边显示"按四川 2026 公式估算的综合分"），不进数据库、不计入方案历史。
     *
     * <p>支持的 candidateType：艺术类 / 体育类（普通类返回 success=false）。</p>
     * <p>支持的 artCategory：美术与设计类 / 音乐表演类 / 舞蹈类 等 11 个统考类别。</p>
     */
    @GetMapping("/sc/composite-score")
    public Result<Map<String, Object>> scCompositeScore(
            @RequestParam(required = false, defaultValue = "艺术类") String candidateType,
            @RequestParam(required = false) String artCategory,
            @RequestParam int cultureScore,
            @RequestParam int professionalScore) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidateType", candidateType);
        payload.put("artCategory", artCategory == null ? "" : artCategory);
        payload.put("cultureScore", cultureScore);
        payload.put("professionalScore", professionalScore);
        payload.put("supportedArtCategories", sichuanCompositeScoreCalculator.listArtCategories());

        sichuanCompositeScoreCalculator.calculate(candidateType, cultureScore, professionalScore, artCategory)
                .ifPresentOrElse(result -> {
                    payload.put("success", true);
                    payload.put("score", result.getScore());
                    payload.put("category", result.getCategory());
                    payload.put("cultureRatio", result.getCultureRatio());
                    payload.put("professionalRatio", result.getProfessionalRatio());
                    payload.put("cultureWeighted", result.getCultureWeighted());
                    payload.put("professionalWeighted", result.getProfessionalWeighted());
                    payload.put("professionalScale", result.getProfessionalScale());
                    payload.put("formula", result.getFormula());
                }, () -> {
                    payload.put("success", false);
                    payload.put("score", 0.0);
                    payload.put("formula", "考生类别 + 统考类别无法识别，无法计算综合分");
                });
        return Result.ok(payload);
    }

    /**
     * 院校专业组省（SC / HB / AH）主链路：跳过贵州专用 RecommendEngineRouter，
     * 直接由 ProfessionalGroupVolunteerService 生成 45 志愿，按 SichuanBatchRuleRegistry
     * 计算 supportLevel / supportReason / engineName，装饰 PlanResult 与 modelInfo。
     */
    private VolunteerService.PlanResult recommendForProfessionalGroupProvince(VolunteerService.GenerateRequest req,
                                                                              PolicyRuleService.PolicyContext policy,
                                                                              String provinceCode,
                                                                              HttpServletRequest httpReq) {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.find(req.getBatchCode())
                .orElseGet(() -> SichuanBatchRuleRegistry.require(SichuanBatchRuleRegistry.DEFAULT_BATCH_CODE));
        BatchSupportService.BatchSupportResponse supportResponse = resolveSichuanSupportMatrix(provinceCode, req.getYear());
        BatchSupportService.BatchSupportItem supportItem = locateSichuanSupportItem(supportResponse, rule);

        boolean mainPipeline = rule.mainRankEngine()
                && rule.recommendMode() == SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                && rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ORDINARY;

        VolunteerService.PlanResult plan;
        if (mainPipeline) {
            // 主流程（SC_BENKE_B / SC_ZHUANKE_B 等普通类院校专业组 45 平行志愿）
            plan = professionalGroupVolunteerService.generate(req, null, getClientIp(httpReq));
        } else {
            // 非主流程批次（艺术 / 体育 / 顺序志愿 / 8 类专项 / 高水平运动队）：
            // 自己构造 PlanResult 骨架（不用 GZ 专用的 QueryOnlyRecommendEngine，避免对 BatchRule 的硬依赖），
            // 然后用 SichuanBatchListingService 在 data_admission_group_line 里按批次关键词查候选注入 items，
            // 有数据时显示 N 条候选 + 综合分/资格审核说明，无数据时 items=空 + dataQualityWarning。
            plan = buildSichuanQueryOnlyPlanSkeleton(req, rule);
            SichuanBatchListingService.BatchListingResult listing =
                    sichuanBatchListingService.listForBatch(req, rule);
            if (listing != null) {
                if (listing.getItems() != null && !listing.getItems().isEmpty()) {
                    plan.setItems(listing.getItems());
                }
                if (listing.getMessage() != null && !listing.getMessage().isBlank()) {
                    plan.setDataQualityWarning(
                            (plan.getDataQualityWarning() == null ? "" : plan.getDataQualityWarning() + " ")
                                    + listing.getMessage());
                }
                if (listing.getWarnings() != null && !listing.getWarnings().isEmpty()) {
                    java.util.List<String> merged = new ArrayList<>(plan.getWarnings() == null ? List.of() : plan.getWarnings());
                    for (String w : listing.getWarnings()) {
                        if (w != null && !w.isBlank() && !merged.contains(w)) merged.add(w);
                    }
                    plan.setWarnings(merged);
                }
            }
        }

        decorateSichuanPlan(req, plan, policy, rule, supportResponse, supportItem, mainPipeline);
        return plan;
    }

    /**
     * 为非主流程 SC 批次构造一个 QUERY_ONLY 模式的 PlanResult 骨架。
     * 后续由 SichuanBatchListingService 填充 items / dataQualityWarning，
     * 由 decorateSichuanPlan 填充 policy / supportLevel / modelInfo / yearContext。
     */
    private VolunteerService.PlanResult buildSichuanQueryOnlyPlanSkeleton(VolunteerService.GenerateRequest req,
                                                                          SichuanBatchRuleRegistry.BatchRule rule) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode(req.getProvinceCode());
        plan.setProvinceName(provincePolicyService.getPolicy(req.getProvinceCode()).getProvinceName());
        plan.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        plan.setVolunteerUnitLabel("院校专业组");
        plan.setTargetBatch(rule.batchName());
        plan.setTargetCount(rule.targetCount());
        plan.setTotalScore(req.getTotalScore());
        plan.setProvinceRank(req.getProvinceRank());
        plan.setFirstSubject(req.getFirstSubject());
        plan.setResubjects(req.getResubjects() == null ? List.of() : req.getResubjects());
        plan.setPreferredMajors(req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        plan.setPreferredRegions(req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        plan.setStrategyMode(req.getStrategyMode() == null ? "均衡型" : req.getStrategyMode());
        plan.setDecisionPriority(req.getDecisionPriority() == null ? "专业优先" : req.getDecisionPriority());
        plan.setCareerGoal(req.getCareerGoal() == null ? "就业优先" : req.getCareerGoal());
        plan.setTuitionBudget(req.getTuitionBudget() == null ? "均衡预算" : req.getTuitionBudget());
        plan.setAcceptPrivate(Boolean.TRUE.equals(req.getAcceptPrivate()));
        plan.setAcceptSinoForeign(Boolean.TRUE.equals(req.getAcceptSinoForeign()));
        plan.setItems(List.of());
        plan.setManualReviewItems(List.of());
        plan.setRecommendMode(rule.recommendMode().name());
        plan.setSupportLevel(com.gzly.service.BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        // v7.50: 非主流程 SC 批次也走 matrix（艺术 → SichuanArtCompositeEngine，体育 → ...Sports...，专项 → ...SpecialPlan...）
        plan.setEngineName(ProvinceBatchEngineMatrix.resolveEngineName(req.getProvinceCode(), rule.batchCode()));
        plan.setSupportReason(rule.supportNote());
        plan.setWarnings(rule.supportNote() == null ? List.of() : new ArrayList<>(List.of(rule.supportNote())));
        plan.setReferenceProbabilityNotice(String.format(
                "本系统输出为四川%s候选列表，按四川省 2026 实施规定显示批次规则与候选明细；"
                        + "本批次按 %s 规则录取，不构成录取承诺，最终以四川省教育考试院和高校招生章程为准。",
                rule.batchName(), rule.recommendMode().name()));
        return plan;
    }

    private BatchSupportService.BatchSupportResponse resolveSichuanSupportMatrix(String provinceCode, Integer year) {
        try {
            return sichuanBatchSupportService.supportMatrix(provinceCode, year);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BatchSupportService.BatchSupportItem locateSichuanSupportItem(
            BatchSupportService.BatchSupportResponse response, SichuanBatchRuleRegistry.BatchRule rule) {
        if (response == null || response.getItems() == null || rule == null) {
            return null;
        }
        return response.getItems().stream()
                .filter(item -> rule.batchCode().equals(item.getBatchCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 安徽 14 批次主链路（与 SC 平行）：跳过贵州专用 RecommendEngineRouter，
     * 主流程批次（AH_BENKE / AH_ZHUANKE 普通类）走 ProfessionalGroupVolunteerService，
     * 非主流程批次（艺术 / 体育 / 顺序志愿 / 提前批 / 专项）走 AnhuiBatchListingService 注入候选 + dataQualityWarning。
     * supportLevel / engineName / yearContext 等装饰口径完全沿用 SC 同款（{@link BatchSupportService.BatchSupportResponse}）。
     */
    private VolunteerService.PlanResult recommendForAnhuiProvince(VolunteerService.GenerateRequest req,
                                                                  PolicyRuleService.PolicyContext policy,
                                                                  String provinceCode,
                                                                  HttpServletRequest httpReq) {
        AnhuiBatchRuleRegistry.BatchRule rule = AnhuiBatchRuleRegistry.find(req.getBatchCode())
                .orElseGet(() -> AnhuiBatchRuleRegistry.require(AnhuiBatchRuleRegistry.DEFAULT_BATCH_CODE));
        BatchSupportService.BatchSupportResponse supportResponse = resolveAnhuiSupportMatrix(provinceCode, req.getYear());
        BatchSupportService.BatchSupportItem supportItem = locateAnhuiSupportItem(supportResponse, rule);

        boolean mainPipeline = rule.mainRankEngine()
                && rule.recommendMode() == AnhuiBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                && rule.category() == AnhuiBatchRuleRegistry.CandidateCategory.ORDINARY;

        VolunteerService.PlanResult plan;
        if (mainPipeline) {
            plan = professionalGroupVolunteerService.generate(req, null, getClientIp(httpReq));
        } else {
            plan = buildAnhuiQueryOnlyPlanSkeleton(req, rule);
            AnhuiBatchListingService.BatchListingResult listing =
                    anhuiBatchListingService.listForBatch(req, rule);
            if (listing != null) {
                if (listing.getItems() != null && !listing.getItems().isEmpty()) {
                    plan.setItems(listing.getItems());
                }
                if (listing.getMessage() != null && !listing.getMessage().isBlank()) {
                    plan.setDataQualityWarning(
                            (plan.getDataQualityWarning() == null ? "" : plan.getDataQualityWarning() + " ")
                                    + listing.getMessage());
                }
                if (listing.getWarnings() != null && !listing.getWarnings().isEmpty()) {
                    java.util.List<String> merged = new ArrayList<>(plan.getWarnings() == null ? List.of() : plan.getWarnings());
                    for (String w : listing.getWarnings()) {
                        if (w != null && !w.isBlank() && !merged.contains(w)) merged.add(w);
                    }
                    plan.setWarnings(merged);
                }
            }
        }

        decorateAnhuiPlan(req, plan, policy, rule, supportResponse, supportItem, mainPipeline);
        return plan;
    }

    /** AH 非主流程 QUERY_ONLY 骨架（与 buildSichuanQueryOnlyPlanSkeleton 同款，仅文案改为安徽）。 */
    private VolunteerService.PlanResult buildAnhuiQueryOnlyPlanSkeleton(VolunteerService.GenerateRequest req,
                                                                        AnhuiBatchRuleRegistry.BatchRule rule) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode(req.getProvinceCode());
        plan.setProvinceName(provincePolicyService.getPolicy(req.getProvinceCode()).getProvinceName());
        plan.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        plan.setVolunteerUnitLabel("院校专业组");
        plan.setTargetBatch(rule.batchName());
        plan.setTargetCount(rule.targetCount());
        plan.setTotalScore(req.getTotalScore());
        plan.setProvinceRank(req.getProvinceRank());
        plan.setFirstSubject(req.getFirstSubject());
        plan.setResubjects(req.getResubjects() == null ? List.of() : req.getResubjects());
        plan.setPreferredMajors(req.getPreferredMajors() == null ? List.of() : req.getPreferredMajors());
        plan.setPreferredRegions(req.getPreferredRegions() == null ? List.of() : req.getPreferredRegions());
        plan.setStrategyMode(req.getStrategyMode() == null ? "均衡型" : req.getStrategyMode());
        plan.setDecisionPriority(req.getDecisionPriority() == null ? "专业优先" : req.getDecisionPriority());
        plan.setCareerGoal(req.getCareerGoal() == null ? "就业优先" : req.getCareerGoal());
        plan.setTuitionBudget(req.getTuitionBudget() == null ? "均衡预算" : req.getTuitionBudget());
        plan.setAcceptPrivate(Boolean.TRUE.equals(req.getAcceptPrivate()));
        plan.setAcceptSinoForeign(Boolean.TRUE.equals(req.getAcceptSinoForeign()));
        plan.setItems(List.of());
        plan.setManualReviewItems(List.of());
        plan.setRecommendMode(rule.recommendMode().name());
        plan.setSupportLevel(com.gzly.service.BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        // v7.50: AH 非主流程批次走 matrix（艺术 → AnhuiArtCompositeEngine，体育 → AnhuiSportsCompositeEngine，
        // 专项 → AnhuiSpecialPlanEligibilityEngine，顺序 → AnhuiSequentialCollegeEngine 等）
        plan.setEngineName(ProvinceBatchEngineMatrix.resolveEngineName(req.getProvinceCode(), rule.batchCode()));
        plan.setSupportReason(rule.supportNote());
        plan.setWarnings(rule.supportNote() == null ? List.of() : new ArrayList<>(List.of(rule.supportNote())));
        plan.setReferenceProbabilityNotice(String.format(
                "本系统输出为安徽%s候选列表，按安徽省 2026 实施规定显示批次规则与候选明细；"
                        + "本批次按 %s 规则录取，不构成录取承诺，最终以安徽省教育招生考试院和高校招生章程为准。",
                rule.batchName(), rule.recommendMode().name()));
        return plan;
    }

    private BatchSupportService.BatchSupportResponse resolveAnhuiSupportMatrix(String provinceCode, Integer year) {
        try {
            return anhuiBatchSupportService.supportMatrix(provinceCode, year);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BatchSupportService.BatchSupportItem locateAnhuiSupportItem(
            BatchSupportService.BatchSupportResponse response, AnhuiBatchRuleRegistry.BatchRule rule) {
        if (response == null || response.getItems() == null || rule == null) {
            return null;
        }
        return response.getItems().stream()
                .filter(item -> rule.batchCode().equals(item.getBatchCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 装饰 AH plan：与 decorateSichuanPlan 同款字段口径（supportLevel / recommendMode / engineName /
     * supportReason / modelInfo / yearContext），仅 BatchRule 来自 AnhuiBatchRuleRegistry。
     * 复用 isPreOfficialDataResponseSichuan + applySichuanYearContext（参数都是省份无关的 BatchSupportResponse）。
     */
    private void decorateAnhuiPlan(VolunteerService.GenerateRequest req,
                                   VolunteerService.PlanResult plan,
                                   PolicyRuleService.PolicyContext policy,
                                   AnhuiBatchRuleRegistry.BatchRule rule,
                                   BatchSupportService.BatchSupportResponse supportResponse,
                                   BatchSupportService.BatchSupportItem supportItem,
                                   boolean mainPipeline) {
        if (plan == null) {
            return;
        }

        List<String> warnings = new ArrayList<>();
        if (policy != null && policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        if (supportItem != null && supportItem.getWarnings() != null) {
            for (String warning : supportItem.getWarnings()) {
                if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
        }
        applySichuanYearContext(plan, supportResponse, warnings);

        Map<String, Object> publicPolicy = policy == null || policy.getConfig() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setPolicy(publicPolicy);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        if (plan.getModelInfo() != null) {
            modelInfo.putAll(plan.getModelInfo());
        }

        String supportLevel = supportItem != null && supportItem.getSupportLevel() != null
                ? supportItem.getSupportLevel()
                : (mainPipeline ? BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name()
                                : BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        String recommendMode = rule.recommendMode().name();
        // v7.50: 走 (province, batch) 矩阵拿专用 engineName（AH_BENKE → AnhuiProfessionalGroup45Engine 等）
        String engineName = ProvinceBatchEngineMatrix.resolveEngineName(
                req.getProvinceCode(), rule.batchCode());
        String supportReason = supportItem != null && supportItem.getSupportReason() != null
                ? supportItem.getSupportReason() : rule.supportNote();
        boolean queryOnly = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name().equals(supportLevel)
                || BatchRuleRegistry.SupportLevel.UNSUPPORTED.name().equals(supportLevel)
                || !mainPipeline;

        if (mainPipeline && isPreOfficialDataResponseSichuan(supportResponse)
                && !BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            supportLevel = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
            recommendMode = BatchRuleRegistry.RecommendMode.QUERY_ONLY.name();
            engineName = QueryOnlyRecommendEngine.NAME;
            queryOnly = true;
            supportReason = AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
        }

        plan.setSupportLevel(supportLevel);
        plan.setRecommendMode(recommendMode);
        plan.setEngineName(engineName);
        plan.setSupportReason(supportReason);
        publicPolicy.put("supportLevel", supportLevel);
        publicPolicy.put("recommendMode", recommendMode);
        publicPolicy.put("engineName", engineName);
        publicPolicy.put("supportReason", supportReason);
        publicPolicy.put("provinceCode", req.getProvinceCode());
        publicPolicy.put("volunteerUnitType", ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        publicPolicy.put("batchCode", rule.batchCode());
        publicPolicy.put("batchName", rule.batchName());

        modelInfo.put("supportLevel", supportLevel);
        modelInfo.put("recommendMode", recommendMode);
        modelInfo.put("engineName", engineName);
        modelInfo.put("supportReason", supportReason);
        modelInfo.put("queryOnly", queryOnly);
        if (queryOnly) {
            modelInfo.put("visibleMetric", "query_only");
        }
        applySichuanYearContext(publicPolicy, modelInfo, supportResponse);
        plan.setModelInfo(modelInfo);
        plan.setWarnings(warnings);
    }

    private RecommendEngineDecision buildSichuanFallbackDecision(SichuanBatchRuleRegistry.BatchRule rule,
                                                                 BatchSupportService.BatchSupportItem item) {
        String supportLevel = item != null && item.getSupportLevel() != null
                ? item.getSupportLevel() : BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        String supportReason = item != null && item.getSupportReason() != null
                ? item.getSupportReason() : rule.supportNote();
        return RecommendEngineDecision.builder()
                .engineName(QueryOnlyRecommendEngine.NAME)
                .recommendMode(BatchRuleRegistry.RecommendMode.QUERY_ONLY.name())
                .supportLevel(supportLevel)
                .maxVolunteerCount(rule.targetCount())
                .queryOnly(true)
                .batchCode(rule.batchCode())
                .batchName(rule.batchName())
                .candidateType(rule.candidateType())
                .supportReason(supportReason)
                .mainRankEngine(rule.mainRankEngine())
                .warnings(rule.supportNote() == null ? List.of() : List.of(rule.supportNote()))
                .build();
    }

    private void decorateSichuanPlan(VolunteerService.GenerateRequest req,
                                     VolunteerService.PlanResult plan,
                                     PolicyRuleService.PolicyContext policy,
                                     SichuanBatchRuleRegistry.BatchRule rule,
                                     BatchSupportService.BatchSupportResponse supportResponse,
                                     BatchSupportService.BatchSupportItem supportItem,
                                     boolean mainPipeline) {
        if (plan == null) {
            return;
        }

        List<String> warnings = new ArrayList<>();
        if (policy != null && policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        if (supportItem != null && supportItem.getWarnings() != null) {
            for (String warning : supportItem.getWarnings()) {
                if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
        }
        applySichuanYearContext(plan, supportResponse, warnings);

        Map<String, Object> publicPolicy = policy == null || policy.getConfig() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setPolicy(publicPolicy);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        if (plan.getModelInfo() != null) {
            modelInfo.putAll(plan.getModelInfo());
        }

        String supportLevel = supportItem != null && supportItem.getSupportLevel() != null
                ? supportItem.getSupportLevel()
                : (mainPipeline ? BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name()
                                : BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        String recommendMode = rule.recommendMode().name();
        // v7.50: 按 (province, batch) 从 ProvinceBatchEngineMatrix 拿专用 engineName，
        // 不再硬编码 ProfessionalGroupVolunteerEngine / QueryOnlyRecommendEngine 二选一。
        String engineName = ProvinceBatchEngineMatrix.resolveEngineName(
                req.getProvinceCode(), rule.batchCode());
        String supportReason = supportItem != null && supportItem.getSupportReason() != null
                ? supportItem.getSupportReason() : rule.supportNote();
        boolean queryOnly = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name().equals(supportLevel)
                || BatchRuleRegistry.SupportLevel.UNSUPPORTED.name().equals(supportLevel)
                || !mainPipeline;

        // 进入 PRE_OFFICIAL_DATA 阶段且当前批次没有历史数据兜底时，统一压回 QUERY_ONLY。
        if (mainPipeline && isPreOfficialDataResponseSichuan(supportResponse)
                && !BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            supportLevel = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
            recommendMode = BatchRuleRegistry.RecommendMode.QUERY_ONLY.name();
            engineName = QueryOnlyRecommendEngine.NAME;
            queryOnly = true;
            supportReason = AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
        }

        plan.setSupportLevel(supportLevel);
        plan.setRecommendMode(recommendMode);
        plan.setEngineName(engineName);
        plan.setSupportReason(supportReason);
        publicPolicy.put("supportLevel", supportLevel);
        publicPolicy.put("recommendMode", recommendMode);
        publicPolicy.put("engineName", engineName);
        publicPolicy.put("supportReason", supportReason);
        publicPolicy.put("provinceCode", req.getProvinceCode());
        publicPolicy.put("volunteerUnitType", ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        publicPolicy.put("batchCode", rule.batchCode());
        publicPolicy.put("batchName", rule.batchName());

        modelInfo.put("supportLevel", supportLevel);
        modelInfo.put("recommendMode", recommendMode);
        modelInfo.put("engineName", engineName);
        modelInfo.put("supportReason", supportReason);
        modelInfo.put("queryOnly", queryOnly);
        if (queryOnly) {
            modelInfo.put("visibleMetric", "query_only");
        }
        applySichuanYearContext(publicPolicy, modelInfo, supportResponse);
        plan.setModelInfo(modelInfo);
        plan.setWarnings(warnings);
    }

    private void applySichuanYearContext(VolunteerService.PlanResult plan,
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
        if (isPreOfficialDataResponseSichuan(supportResponse) && !warnings.contains(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING)) {
            warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        }
    }

    private void applySichuanYearContext(Map<String, Object> publicPolicy,
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
        publicPolicy.putAll(target);
        modelInfo.putAll(target);
    }

    private boolean isPreOfficialDataResponseSichuan(BatchSupportService.BatchSupportResponse response) {
        return response != null
                && (response.isEstimateMode()
                || AdmissionYearService.PHASE_PRE_OFFICIAL_DATA.equals(response.getRecommendationPhase()));
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
            if (rule.mainRankEngine() && isPreOfficialDataResponse(supportResponse)
                    && !BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
                // 当前年份官方数据尚未发布且 engine 没有判定为试推荐时（如批次不在历史回退白名单或没有历史数据），
                // 仍按 QUERY_ONLY 收口；engine 已判 TRIAL_RECOMMEND 的批次（普通本科批 / 高职专科批 + 有 2024/2025
                // 历史数据）保留试推荐口径，对应前端 96 志愿草稿和 AI 解读入口可见。
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
        req.setBatchCode(policyRuleService.normalizeBatchCode(provinceCode, req.getBatchCode()));
        if (ProvincePolicyService.AH.equals(provinceCode)) {
            req.setCandidateType(AnhuiBatchRuleRegistry.normalizeCandidateType(req.getCandidateType()));
        } else if (provincePolicyService.isProfessionalGroupProvince(provinceCode)) {
            req.setCandidateType(SichuanBatchRuleRegistry.normalizeCandidateType(req.getCandidateType()));
        } else {
            req.setCandidateType(BatchRuleRegistry.normalizeCandidateType(req.getCandidateType()));
        }
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
        applyPolicyToRequest(req, config, ProvincePolicyService.GZ);
    }

    private void applyPolicyToRequest(VolunteerService.GenerateRequest req,
                                      com.gzly.entity.PolicyRuleConfig config,
                                      String provinceCode) {
        if (req == null || config == null) {
            return;
        }
        req.setPolicyMaxVolunteerCount(config.getMaxVolunteerCount());
        req.setPolicyBatchName(config.getBatchName());
        req.setPolicyVolunteerUnitLabel(config.getVolunteerMode());
        req.setPolicyVolunteerUnitType(provincePolicyService.isProfessionalGroupProvince(provinceCode)
                ? ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45
                : ProvincePolicyService.UNIT_MAJOR_96);
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
