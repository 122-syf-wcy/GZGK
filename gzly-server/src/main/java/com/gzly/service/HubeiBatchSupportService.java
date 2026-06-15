package com.gzly.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 湖北 HB_* 批次支持矩阵。
 *
 * <p>PRE 阶段只读统计历史组线行数，不写库、不导入数据、不改变推荐算法。</p>
 */
@Service
@RequiredArgsConstructor
public class HubeiBatchSupportService {

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year) {
        return supportMatrix(provinceCode, year, false);
    }

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year,
                                                                  boolean publicYearLocked) {
        String normalizedProvince = ProvincePolicyService.HB;
        int resolvedYear = year == null || year <= 0 ? admissionYearService.getActiveAdmissionYear() : year;

        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setProvinceCode(normalizedProvince);
        response.setYear(resolvedYear);
        response.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        response.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        response.setHistoryYears(admissionYearService.resolveHistoryYears(admissionYearService.getActiveAdmissionYear()));
        response.setTrainingYears(admissionYearService.resolveTrainingYears());
        response.setTargetYear(admissionYearService.getTargetYear());
        response.setFutureImportYear(admissionYearService.getFutureImportYear());
        response.setPublicYearLocked(publicYearLocked);

        BatchSupportService.DataReadiness readiness = conservativeReadiness(normalizedProvince, resolvedYear);
        response.setDataReadiness(readiness);
        response.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        response.setOfficialDataReady(false);
        response.setEstimateMode(true);
        response.setDataSourceYears(response.getTrainingYears());

        List<BatchSupportService.BatchSupportItem> items = HubeiBatchRuleRegistry.allRules().stream()
                .map(rule -> buildItem(rule, response.getTrainingYears(), readiness))
                .toList();
        response.setItems(items);
        response.setSummary(summaryFor(items));
        return response;
    }

    private BatchSupportService.DataReadiness conservativeReadiness(String provinceCode, int year) {
        BatchSupportService.DataReadiness base = null;
        try {
            base = dataYearReadinessService == null ? null : dataYearReadinessService.getOrDefaultReadiness(provinceCode, year);
        } catch (Exception ignored) {
        }
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        readiness.setProvinceCode(provinceCode);
        readiness.setYear(year);
        readiness.setPolicyReady(false);
        readiness.setScoreSegmentReady(false);
        readiness.setAdmissionPlanReady(false);
        readiness.setMajorRequirementReady(false);
        readiness.setMajorMetaReady(false);
        readiness.setMlTrainingReady(false);
        readiness.setHistoricalTrainingReady(base == null || base.isHistoricalTrainingReady());
        readiness.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        readiness.setLatestImportBatchId("");
        readiness.setLastCheckedAt(base == null ? "" : base.getLastCheckedAt());
        readiness.setRemarks("湖北 2026 官方招生计划、院校专业组目录和投档线待发布；当前仅使用已核验历史数据做估算参考。");
        return readiness;
    }

    private BatchSupportService.BatchSupportItem buildItem(HubeiBatchRuleRegistry.BatchRule rule,
                                                           List<Integer> trainingYears,
                                                           BatchSupportService.DataReadiness readiness) {
        long historicalRows = rule.mainRankEngine() ? countHistoricalGroupLines(trainingYears) : 0L;
        String supportLevel = rule.mainRankEngine() && historicalRows <= 0
                ? BatchRuleRegistry.SupportLevel.QUERY_ONLY.name()
                : rule.supportLevel();
        BatchSupportService.BatchSupportItem item = new BatchSupportService.BatchSupportItem();
        item.setBatchCode(rule.batchCode());
        item.setBatchName(rule.batchName());
        item.setCandidateType(rule.candidateType());
        item.setCategory(rule.category().name());
        item.setSupportLevel(supportLevel);
        item.setRecommendMode(rule.recommendMode().name());
        item.setEngine(rule.engine());
        item.setEngineName(rule.engine());
        item.setTargetCount(rule.targetCount());
        item.setMaxVolunteerCount(rule.targetCount());
        item.setMajorPerSchoolCount(rule.majorsPerGroup());
        item.setHasAdjustment(rule.hasAdjustment());
        item.setVolunteerMode(rule.volunteerMode());
        item.setPolicyConfigured(true);
        item.setPolicyStatus("registry_only");
        item.setScoreLineCount(0);
        item.setMajorScoreCount(0);
        item.setHistoricalScoreLineCount(historicalRows);
        item.setHistoricalMajorScoreCount(0);
        item.setPlanCount(0);
        item.setRequirementCount(0);
        item.setDataStatus(dataStatus(rule, supportLevel, historicalRows));
        item.setMissingData(missingData(rule));
        item.setSupportNote(rule.supportNote());
        item.setSupportReason(supportReason(rule, supportLevel, historicalRows));
        item.setWarnings(List.of(
                AdmissionYearService.PRE_OFFICIAL_DATA_WARNING,
                rule.supportNote(),
                "湖北结果仅供志愿填报参考，不等于正式录取保证。"
        ));
        return item;
    }

    private BatchSupportService.DataStatus dataStatus(HubeiBatchRuleRegistry.BatchRule rule,
                                                      String supportLevel,
                                                      long historicalRows) {
        BatchSupportService.DataStatus status = new BatchSupportService.DataStatus();
        status.setPolicyCount(1);
        status.setScoreLineCount(0);
        status.setMajorScoreCount(0);
        status.setHistoryCount(historicalRows);
        status.setPlanCount(0);
        status.setRequirementCount(0);
        status.setReady(false);
        status.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        if (rule.mainRankEngine() && BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            status.setDetail("湖北本科普通批可基于历史院校专业组数据生成参考志愿草稿；2026 官方数据待发布。");
        } else if (rule.mainRankEngine()) {
            status.setDetail("湖北本科普通批历史院校专业组数据不足，当前仅展示策略和数据缺口。");
        } else {
            status.setDetail("湖北该批次需要 2026 官方计划、资格和录取规则，当前仅展示策略说明。");
        }
        return status;
    }

    private List<String> missingData(HubeiBatchRuleRegistry.BatchRule rule) {
        if (rule.mainRankEngine()) {
            return List.of("HB_2026_plan", "HB_2026_score_rank", "HB_2026_group_line", "HB_2026_major_requirement");
        }
        return List.of("HB_2026_policy", "HB_2026_plan", "HB_2026_qualification_or_composite_rule");
    }

    private String supportReason(HubeiBatchRuleRegistry.BatchRule rule, String supportLevel, long historicalRows) {
        if (rule.mainRankEngine() && BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name().equals(supportLevel)) {
            return "当前为 PRE_OFFICIAL_DATA 历史估算能力，已检测到湖北历史院校专业组数据，可生成参考志愿草稿。";
        }
        return rule.supportNote();
    }

    private long countHistoricalGroupLines(List<Integer> years) {
        if (jdbcTemplate == null || years == null || years.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Integer year : years) {
            if (year == null || year <= 0) {
                continue;
            }
            try {
                Long value = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM data_admission_group_line
                        WHERE province_code = ?
                          AND year = ?
                          AND min_rank IS NOT NULL
                          AND min_rank > 0
                        """, Long.class, ProvincePolicyService.HB, year);
                total += value == null ? 0L : value;
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return total;
    }

    private Map<String, Long> summaryFor(List<BatchSupportService.BatchSupportItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String level : List.of("FULL_RECOMMEND", "TRIAL_RECOMMEND", "QUERY_ONLY", "UNSUPPORTED")) {
            summary.put(level, items.stream().filter(item -> level.equals(item.getSupportLevel())).count());
        }
        return summary;
    }
}
