package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProvinceReadinessService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 省份批次支持状态接口。
 *
 * <p>前端 8 个地区首页一直在调用 `/volunteer/{code}/batch-support`，但后端此前不存在该接口，
 * 导致所有地区页常驻"数据状态暂时读取失败"。本接口补齐该契约，响应结构对齐前端
 * `gzly-web/src/api/volunteer.ts` 的 `ProvinceBatchSupportResponse` / `BatchSupportItem` 定义。</p>
 *
 * <p>supportLevel 与就绪度四态的映射：FULL→FULL_RECOMMEND、ESTIMATE→ESTIMATE_RECOMMEND、
 * QUERY_ONLY/LOCKED→QUERY_ONLY（LOCKED 额外带 missingData）。</p>
 */
@RestController
@RequestMapping("/volunteer")
@RequiredArgsConstructor
public class ProvinceBatchSupportController {

    private static final String PHASE_PRE_OFFICIAL_DATA = "PRE_OFFICIAL_DATA";

    private final ProvincePolicyService provincePolicyService;
    private final ProvinceReadinessService provinceReadinessService;
    private final PolicyRuleService policyRuleService;

    @GetMapping("/{provinceCode}/batch-support")
    public Result<BatchSupportResponse> batchSupport(@PathVariable String provinceCode,
                                                     @RequestParam(required = false, defaultValue = "2026") Integer year) {
        String code = provincePolicyService.normalizeProvinceCode(provinceCode);
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(code);
        ProvinceReadinessService.ProvinceReadiness readiness = provinceReadinessService.getReadiness(code);

        BatchSupportResponse response = new BatchSupportResponse();
        response.setProvinceCode(code);
        response.setProvinceName(policy.getProvinceName());
        response.setYear(year);
        response.setTargetYear(year);
        response.setLatestOfficialDataYear(readiness.getLatestDataYear());
        response.setDataSourceYears(readiness.getDataYears());
        response.setRecommendationPhase(PHASE_PRE_OFFICIAL_DATA);
        response.setOfficialDataReady(false);
        response.setReadinessLevel(readiness.getLevel());

        List<BatchSupportItem> items = new ArrayList<>();
        List<PolicyRuleConfig> configs = policyRuleService.listPolicies(code, year);
        if (configs.isEmpty()) {
            // 政策表无该省该年数据（如生产库未跑种子）时，用内置省份口径兜底展示，并明确标注未配置。
            items.add(fallbackItem(policy, readiness));
            response.getWarnings().add(String.format("%d年%s批次政策尚未在政策库配置，以下为系统内置口径，仅供展示。",
                    year, policy.getProvinceName()));
        } else {
            for (PolicyRuleConfig config : configs) {
                items.add(toItem(config, policy, readiness));
            }
        }
        response.setItems(items);

        Map<String, Integer> summary = new LinkedHashMap<>();
        for (BatchSupportItem item : items) {
            summary.merge(item.getSupportLevel(), 1, Integer::sum);
        }
        response.setSummary(summary);

        readiness.getSubjects().stream()
                .map(ProvinceReadinessService.SubjectReadiness::getReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .forEach(response.getWarnings()::add);
        return Result.ok(response);
    }

    private BatchSupportItem toItem(PolicyRuleConfig config,
                                    ProvincePolicyService.ProvincePolicy policy,
                                    ProvinceReadinessService.ProvinceReadiness readiness) {
        BatchSupportItem item = new BatchSupportItem();
        item.setBatchCode(config.getBatchCode());
        item.setBatchName(config.getBatchName());
        item.setCandidateType(config.getCandidateType());
        item.setTargetCount(config.getMaxVolunteerCount() == null ? policy.getTargetCount() : config.getMaxVolunteerCount());
        item.setMaxVolunteerCount(item.getTargetCount());
        item.setVolunteerMode(config.getVolunteerMode());
        item.setPolicyStatus(config.getPolicyStatus());
        item.setOfficialSourceTitle(config.getOfficialSourceTitle());
        item.setOfficialSourceUrl(config.getOfficialSourceUrl());
        // 就绪度按普通类本科批判定；其余批次（提前批等）暂一律 QUERY_ONLY，后续按批次数据单独判定。
        boolean mainBatch = PolicyRuleService.DEFAULT_BATCH_CODE.equals(config.getBatchCode());
        applyReadiness(item, readiness, mainBatch);
        return item;
    }

    private BatchSupportItem fallbackItem(ProvincePolicyService.ProvincePolicy policy,
                                          ProvinceReadinessService.ProvinceReadiness readiness) {
        BatchSupportItem item = new BatchSupportItem();
        item.setBatchCode(PolicyRuleService.DEFAULT_BATCH_CODE);
        item.setBatchName(policy.getTargetBatch());
        item.setCandidateType(PolicyRuleService.DEFAULT_CANDIDATE_TYPE);
        item.setTargetCount(policy.getTargetCount());
        item.setMaxVolunteerCount(policy.getTargetCount());
        item.setVolunteerMode(policy.getVolunteerUnitLabel());
        item.setPolicyStatus("unconfigured");
        applyReadiness(item, readiness, true);
        return item;
    }

    private void applyReadiness(BatchSupportItem item,
                                ProvinceReadinessService.ProvinceReadiness readiness,
                                boolean mainBatch) {
        String level = mainBatch ? readiness.getLevel() : ProvinceReadinessService.LEVEL_QUERY_ONLY;
        item.setSupportLevel(switch (level) {
            case ProvinceReadinessService.LEVEL_FULL -> "FULL_RECOMMEND";
            case ProvinceReadinessService.LEVEL_ESTIMATE -> "ESTIMATE_RECOMMEND";
            default -> "QUERY_ONLY";
        });
        item.setGeneratorReady(mainBatch
                && (ProvinceReadinessService.LEVEL_ESTIMATE.equals(level)
                || ProvinceReadinessService.LEVEL_FULL.equals(level)));
        List<String> missing = new ArrayList<>();
        StringBuilder reason = new StringBuilder();
        for (ProvinceReadinessService.SubjectReadiness subject : readiness.getSubjects()) {
            missing.addAll(subject.getMissingData());
            if (subject.getReason() != null && !subject.getReason().isBlank()) {
                if (reason.length() > 0) reason.append(' ');
                reason.append(subject.getReason());
            }
        }
        item.setMissingData(missing.stream().distinct().toList());
        item.setSupportReason(mainBatch
                ? reason.toString()
                : "非普通类本科批暂只提供政策查询，不进入智能生成。");
    }

    @Data
    public static class BatchSupportResponse {
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private Integer targetYear;
        private Integer latestOfficialDataYear;
        private List<Integer> dataSourceYears = new ArrayList<>();
        private String recommendationPhase;
        private boolean officialDataReady;
        /** 省级就绪度聚合：LOCKED / QUERY_ONLY / ESTIMATE / FULL */
        private String readinessLevel;
        private List<BatchSupportItem> items = new ArrayList<>();
        private Map<String, Integer> summary = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class BatchSupportItem {
        private String batchCode;
        private String batchName;
        private String candidateType;
        private String supportLevel;
        private int targetCount;
        private int maxVolunteerCount;
        private String volunteerMode;
        private String policyStatus;
        private String officialSourceTitle;
        private String officialSourceUrl;
        private boolean generatorReady;
        private String supportReason;
        private List<String> missingData = new ArrayList<>();
    }
}
