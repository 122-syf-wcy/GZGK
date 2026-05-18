package com.gzly.service;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.recommend.QueryOnlyRecommendEngine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v7.54 重构：从 {@code VolunteerRecommendController.decorateSichuanPlan} 与
 * {@code decorateAnhuiPlan} 中抽出来的"省份无关"装饰逻辑。
 *
 * <p>两份 decorate 方法的差异仅在于 BatchRule 类型（{@link SichuanBatchRuleRegistry.BatchRule}
 * vs {@link AnhuiBatchRuleRegistry.BatchRule}），但拿到的核心字段都是 String：
 * batchCode / batchName / recommendMode / supportNote。本类只接受 String 参数，
 * 让 SC / AH 两个 controller 路径共享同一套 supportLevel / engineName / publicPolicy /
 * modelInfo / queryOnly 决策。</p>
 *
 * <p>不破坏现有 controller 方法签名；它们继续从 Registry 拿 BatchRule，再调本类的 String-only
 * 入口。这样 controller 整体结构不变，但 ~80 行的 supportLevel/decorate 逻辑只写一遍。</p>
 */
public final class ProvincePlanDecorator {

    private ProvincePlanDecorator() {
    }

    /**
     * 计算 plan 最终的 supportLevel / recommendMode / engineName / supportReason，
     * 并填充 publicPolicy + modelInfo Map，最后写回 plan。
     *
     * <p>语义保持与原 decorateSichuanPlan / decorateAnhuiPlan 完全一致：</p>
     * <ul>
     *   <li>supportLevel 优先取 supportItem.getSupportLevel()，否则按 mainPipeline 决定 TRIAL/QUERY</li>
     *   <li>engineName 由调用方从 {@link com.gzly.service.recommend.ProvinceBatchEngineMatrix} 取，不在本类决策</li>
     *   <li>PRE_OFFICIAL_DATA 且 supportLevel != TRIAL_RECOMMEND 时强制压回 QUERY_ONLY</li>
     * </ul>
     *
     * @return queryOnly 标记，用于 modelInfo.visibleMetric 等下游逻辑
     */
    public static FinalDecoration apply(
            String provinceCode,
            String batchCode,
            String batchName,
            String recommendModeName,
            String supportNote,
            String engineName,
            VolunteerService.PlanResult plan,
            PolicyRuleService.PolicyContext policy,
            BatchSupportService.BatchSupportResponse supportResponse,
            BatchSupportService.BatchSupportItem supportItem,
            boolean mainPipeline,
            PolicyRuleService policyRuleService) {

        String supportLevel = supportItem != null && supportItem.getSupportLevel() != null
                ? supportItem.getSupportLevel()
                : (mainPipeline ? BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name()
                                : BatchRuleRegistry.SupportLevel.QUERY_ONLY.name());
        String recommendMode = recommendModeName;
        String resolvedEngineName = engineName;
        String supportReason = supportItem != null && supportItem.getSupportReason() != null
                ? supportItem.getSupportReason() : supportNote;
        boolean queryOnly = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name().equals(supportLevel)
                || BatchRuleRegistry.SupportLevel.UNSUPPORTED.name().equals(supportLevel)
                || !mainPipeline;

        // 进入 PRE_OFFICIAL_DATA 阶段且当前批次没有历史数据兜底 → 强制 QUERY_ONLY
        if (mainPipeline && isPreOfficialDataResponse(supportResponse)
                && !BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            supportLevel = BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
            recommendMode = BatchRuleRegistry.RecommendMode.QUERY_ONLY.name();
            resolvedEngineName = QueryOnlyRecommendEngine.NAME;
            queryOnly = true;
            supportReason = AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
        }

        plan.setSupportLevel(supportLevel);
        plan.setRecommendMode(recommendMode);
        plan.setEngineName(resolvedEngineName);
        plan.setSupportReason(supportReason);

        Map<String, Object> publicPolicy = policy == null || policy.getConfig() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setPolicy(publicPolicy);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        if (plan.getModelInfo() != null) {
            modelInfo.putAll(plan.getModelInfo());
        }

        publicPolicy.put("supportLevel", supportLevel);
        publicPolicy.put("recommendMode", recommendMode);
        publicPolicy.put("engineName", resolvedEngineName);
        publicPolicy.put("supportReason", supportReason);
        publicPolicy.put("provinceCode", provinceCode);
        publicPolicy.put("volunteerUnitType", ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        publicPolicy.put("batchCode", batchCode);
        publicPolicy.put("batchName", batchName);

        modelInfo.put("supportLevel", supportLevel);
        modelInfo.put("recommendMode", recommendMode);
        modelInfo.put("engineName", resolvedEngineName);
        modelInfo.put("supportReason", supportReason);
        modelInfo.put("queryOnly", queryOnly);
        if (queryOnly) {
            modelInfo.put("visibleMetric", "query_only");
        }
        plan.setModelInfo(modelInfo);

        return new FinalDecoration(supportLevel, recommendMode, resolvedEngineName,
                supportReason, queryOnly, publicPolicy, modelInfo);
    }

    public static boolean isPreOfficialDataResponse(BatchSupportService.BatchSupportResponse response) {
        return response != null
                && (response.isEstimateMode()
                || AdmissionYearService.PHASE_PRE_OFFICIAL_DATA.equals(response.getRecommendationPhase()));
    }

    /** 装饰结果（便于上层进一步追加 year context 等）。 */
    public record FinalDecoration(
            String supportLevel,
            String recommendMode,
            String engineName,
            String supportReason,
            boolean queryOnly,
            Map<String, Object> publicPolicy,
            Map<String, Object> modelInfo
    ) {
        public List<String> emptyWarnings() {
            return List.of();
        }
    }
}
