package com.gzly.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class BatchSupportService {

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;

    /**
     * v7.41 高并发优化：supportMatrix 在 form 加载、批次切换、recommend / generate 链路里
     * 每次都被调用，内部要跑 8+ DB 查询（policy_rule_config、information_schema、各 data_*_gz
     * 表 GROUP BY batch 等）。同省 + 同年 + publicYearLocked 维度的结果在 24h 内基本不变，
     * 缓存 5 分钟可以彻底避开 hot path 的 DB 反复查询，500 并发实测预期省掉 ≈80% 重复查询。
     */
    private static final Duration BATCH_SUPPORT_CACHE_TTL = Duration.ofMinutes(5);
    private final Cache<String, BatchSupportResponse> batchSupportCache = Caffeine.newBuilder()
            .expireAfterWrite(BATCH_SUPPORT_CACHE_TTL)
            .maximumSize(256)
            .recordStats()
            .build();

    public BatchSupportService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new AdmissionYearService());
    }

    public BatchSupportService(JdbcTemplate jdbcTemplate, AdmissionYearService admissionYearService) {
        this(jdbcTemplate, admissionYearService, new DataYearReadinessService(jdbcTemplate, admissionYearService));
    }

    @Autowired
    public BatchSupportService(JdbcTemplate jdbcTemplate,
                               AdmissionYearService admissionYearService,
                               DataYearReadinessService dataYearReadinessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
        this.dataYearReadinessService = dataYearReadinessService;
    }

    public BatchSupportResponse supportMatrix(String provinceCode, Integer year) {
        return supportMatrix(provinceCode, year, false);
    }

    public BatchSupportResponse supportMatrix(String provinceCode, Integer year, boolean publicYearLocked) {
        int resolvedYear = year == null || year <= 0 ? admissionYearService.getActiveAdmissionYear() : year;
        String normalizedProvince = provinceCode == null || provinceCode.isBlank() ? "GZ" : provinceCode.trim();
        String cacheKey = normalizedProvince.toUpperCase(Locale.ROOT) + ":" + resolvedYear + ":" + publicYearLocked;
        try {
            return batchSupportCache.get(cacheKey,
                    key -> doSupportMatrix(normalizedProvince, resolvedYear, publicYearLocked));
        } catch (RuntimeException e) {
            // Caffeine loader 任何异常都视为 cache miss，回源直查；不影响线上可用性。
            log.warn("batchSupportCache 读取失败，回源直查 key={}", cacheKey, e);
            return doSupportMatrix(normalizedProvince, resolvedYear, publicYearLocked);
        }
    }

    /**
     * 失效缓存。后台手动改数据后可显式调用。
     */
    public void invalidateSupportMatrixCache() {
        batchSupportCache.invalidateAll();
    }

    /**
     * 暴露缓存统计供 actuator/prometheus 监控热度。
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats supportMatrixCacheStats() {
        return batchSupportCache.stats();
    }

    /**
     * v7.54：暴露 Caffeine 缓存实例给 {@code CaffeineCacheMetricsConfig}，
     * 把 hit/miss/load/eviction 指标注入到 Micrometer + Prometheus，
     * 之前只有 .recordStats() 但没注册到 MeterRegistry。
     */
    public Cache<String, BatchSupportResponse> getCacheForMetrics() {
        return batchSupportCache;
    }

    private BatchSupportResponse doSupportMatrix(String provinceCode, int resolvedYear, boolean publicYearLocked) {
        NextProvincePolicyRegistry.Profile nextProvinceProfile =
                NextProvincePolicyRegistry.find(provinceCode).orElse(null);
        if (nextProvinceProfile != null) {
            return nextProvinceSupportMatrix(nextProvinceProfile, resolvedYear, publicYearLocked);
        }
        BatchSupportResponse response = new BatchSupportResponse();
        response.setProvinceCode(provinceCode);
        response.setYear(resolvedYear);
        response.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        response.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        response.setHistoryYears(admissionYearService.resolveHistoryYears(admissionYearService.getActiveAdmissionYear()));
        response.setTrainingYears(admissionYearService.resolveTrainingYears());
        response.setTargetYear(admissionYearService.getTargetYear());
        response.setFutureImportYear(admissionYearService.getFutureImportYear());
        response.setPublicYearLocked(publicYearLocked);
        DataReadiness readiness = dataYearReadinessService.getOrDefaultReadiness(response.getProvinceCode(), resolvedYear);
        response.setDataReadiness(readiness);
        response.setRecommendationPhase(readiness.getRecommendationPhase());
        response.setOfficialDataReady(admissionYearService.isOfficialDataReady(readiness));
        response.setEstimateMode(admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase()));
        response.setDataSourceYears(resolveDataSourceYears(readiness, response.getTrainingYears(), resolvedYear));
        List<BatchSupportItem> items = new ArrayList<>();
        for (BatchRuleRegistry.BatchRule rule : BatchRuleRegistry.allRules()) {
            items.add(buildItem(rule, response.getProvinceCode(), resolvedYear, readiness, response.getDataSourceYears()));
        }
        response.setItems(items);
        response.setSummary(buildSummary(items));
        return response;
    }

    private BatchSupportResponse nextProvinceSupportMatrix(NextProvincePolicyRegistry.Profile profile,
                                                           int resolvedYear,
                                                           boolean publicYearLocked) {
        BatchSupportResponse response = new BatchSupportResponse();
        response.setProvinceCode(profile.provinceCode());
        response.setYear(resolvedYear);
        response.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        response.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        response.setHistoryYears(admissionYearService.resolveHistoryYears(admissionYearService.getActiveAdmissionYear()));
        response.setTrainingYears(admissionYearService.resolveTrainingYears());
        response.setTargetYear(admissionYearService.getTargetYear());
        response.setFutureImportYear(admissionYearService.getFutureImportYear());
        response.setPublicYearLocked(publicYearLocked);
        DataReadiness readiness = nextProvinceReadiness(profile, resolvedYear);
        response.setDataReadiness(readiness);
        response.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        response.setCapabilityStatus(NextProvincePolicyRegistry.isLevelOneAiQaOnly(profile.provinceCode())
                ? "AI_QA_ONLY" : "HISTORY_ESTIMATE_READY");
        response.setOfficialDataReady(false);
        response.setEstimateMode(true);
        response.setDataSourceYears(response.getTrainingYears());
        List<BatchSupportItem> items = profile.batches().stream()
                .map(batch -> nextProvinceItem(profile, batch))
                .toList();
        response.setItems(items);
        response.setSummary(buildSummary(response.getItems()));
        return response;
    }

    private DataReadiness nextProvinceReadiness(NextProvincePolicyRegistry.Profile profile, int resolvedYear) {
        DataReadiness readiness = new DataReadiness();
        readiness.setProvinceCode(profile.provinceCode());
        readiness.setYear(resolvedYear);
        readiness.setPolicyReady(false);
        readiness.setScoreSegmentReady(false);
        readiness.setAdmissionPlanReady(false);
        readiness.setMajorRequirementReady(false);
        readiness.setMajorMetaReady(false);
        readiness.setMlTrainingReady(false);
        readiness.setHistoricalTrainingReady(true);
        readiness.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        readiness.setRemarks(profile.dataStatusDetail());
        return readiness;
    }

    private BatchSupportItem nextProvinceItem(NextProvincePolicyRegistry.Profile profile,
                                              NextProvincePolicyRegistry.BatchProfile batch) {
        BatchSupportItem item = new BatchSupportItem();
        item.setBatchCode(batch.batchCode());
        item.setBatchName(batch.batchName());
        item.setCandidateType(batch.candidateType());
        item.setCategory(batch.category());
        item.setRecommendMode(batch.recommendMode());
        item.setEngine(batch.engineName());
        item.setEngineName(batch.engineName());
        item.setTargetCount(batch.targetCount());
        item.setMaxVolunteerCount(batch.targetCount());
        item.setMajorPerSchoolCount(batch.majorPerSchoolCount());
        item.setHasAdjustment(batch.hasAdjustment());
        item.setVolunteerMode(batch.volunteerMode());
        item.setSupportNote(batch.supportNote());
        item.setPolicyConfigured(true);
        item.setPolicyStatus("registry_only");
        item.setSupportLevel(batch.supportLevel());
        item.setHistoricalScoreLineCount(batch.ordinaryEstimate() ? 1L : 0L);
        item.setDataStatus(nextProvinceDataStatus(profile, batch));
        item.setMissingData(batch.missingData());
        item.setSupportReason(batch.ordinaryEstimate()
                ? "当前为历史估算能力，基于 2024/2025 数据窗口展示趋势和缺口，不开放完整推荐。"
                : batch.supportNote());
        item.setWarnings(NextProvincePolicyRegistry.isLevelOneAiQaOnly(profile.provinceCode())
                ? List.of(profile.dataStatusDetail(), batch.supportNote())
                : List.of(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING, profile.dataStatusDetail(), batch.supportNote()));
        return item;
    }

    private DataStatus nextProvinceDataStatus(NextProvincePolicyRegistry.Profile profile,
                                              NextProvincePolicyRegistry.BatchProfile batch) {
        DataStatus status = new DataStatus();
        status.setPolicyCount(1);
        status.setScoreLineCount(0);
        status.setMajorScoreCount(0);
        status.setHistoryCount(batch.ordinaryEstimate() ? 1L : 0L);
        status.setPlanCount(0);
        status.setRequirementCount(0);
        status.setStatus(NextProvincePolicyRegistry.isLevelOneAiQaOnly(profile.provinceCode())
                ? "OFFICIAL_DATA_PENDING"
                : AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        status.setDetail(NextProvincePolicyRegistry.isLevelOneAiQaOnly(profile.provinceCode())
                ? profile.dataStatusDetail()
                : batch.ordinaryEstimate()
                ? profile.provinceName() + "普通主批可展示历史估算能力；2026 官方数据待发布。"
                : profile.dataStatusDetail());
        status.setReady(false);
        return status;
    }

    private BatchSupportItem buildItem(BatchRuleRegistry.BatchRule rule,
                                       String provinceCode,
                                       int year,
                                       DataReadiness readiness,
                                       List<Integer> dataSourceYears) {
        PolicySnapshot policy = policySnapshot(provinceCode, year, rule);
        BatchSupportItem item = new BatchSupportItem();
        item.setBatchCode(rule.batchCode());
        item.setBatchName(rule.batchName());
        item.setCandidateType(rule.candidateType());
        item.setCategory(rule.category().name());
        item.setRecommendMode(rule.recommendMode().name());
        item.setEngine(rule.engine());
        item.setEngineName(rule.engine());
        item.setTargetCount(rule.targetCount());
        item.setMaxVolunteerCount(policy.maxVolunteerCount() > 0 ? policy.maxVolunteerCount() : rule.targetCount());
        item.setMajorPerSchoolCount(policy.majorPerSchoolCount());
        item.setHasAdjustment(policy.hasAdjustment());
        item.setVolunteerMode(rule.volunteerMode());
        item.setSupportNote(rule.supportNote());
        item.setPolicyConfigured(policy.configured());
        item.setPolicyStatus(policy.status());
        item.setScoreLineCount(countByBatch("data_score_line_gz", year, rule));
        item.setMajorScoreCount(countByBatch("data_major_score_gz", year, rule));
        item.setHistoricalScoreLineCount(countByBatchAcrossYears("data_score_line_gz", dataSourceYears, rule));
        item.setHistoricalMajorScoreCount(countByBatchAcrossYears("data_major_score_gz", dataSourceYears, rule));
        item.setRequirementCount(countRequirements(year));
        item.setPlanCount(countPlanRows(year, rule));
        item.setSupportLevel(resolveSupportLevel(rule, item, readiness, year));
        item.setDataStatus(dataStatus(item, readiness, year));
        item.setMissingData(missingData(rule, item, readiness, year));
        item.setSupportReason(supportReason(rule, item, readiness, year));
        item.setWarnings(warnings(rule, item, readiness, year));
        return item;
    }

    private String resolveSupportLevel(BatchRuleRegistry.BatchRule rule, BatchSupportItem item, DataReadiness readiness, int year) {
        if (!rule.mainRankEngine()) {
            return rule.baseSupportLevel().name();
        }
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                    && supportsHistoricalReferenceRecommend(rule)
                    && item.isPolicyConfigured()
                    && historicalReferenceCount(item) > 0) {
                return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
            }
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        boolean hasHistory = item.getScoreLineCount() + item.getMajorScoreCount() > 0;
        if (!item.isPolicyConfigured() || !hasHistory) {
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        if (year == admissionYearService.getTargetYear()
                && (!admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                || !readiness.isMajorMetaReady()
                || item.getPlanCount() <= 0
                || item.getRequirementCount() <= 0)) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        return rule.baseSupportLevel().name();
    }

    private DataStatus dataStatus(BatchSupportItem item, DataReadiness readiness, int year) {
        DataStatus dataStatus = new DataStatus();
        dataStatus.setPolicyCount(item.isPolicyConfigured() ? 1 : 0);
        dataStatus.setScoreLineCount(item.getScoreLineCount());
        dataStatus.setMajorScoreCount(item.getMajorScoreCount());
        dataStatus.setHistoryCount(historyCountForDisplay(item, readiness, year));
        dataStatus.setPlanCount(item.getPlanCount());
        dataStatus.setRequirementCount(item.getRequirementCount());
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
                dataStatus.setStatus(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
                dataStatus.setDetail("目标年份官方数据正在分批导入和质检，关键数据尚未全部就绪");
            } else if (supportsHistoricalReferenceRecommendByCode(item.getBatchCode()) && historicalReferenceCount(item) > 0) {
                dataStatus.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
                dataStatus.setDetail("目标年份官方数据尚未发布；当前可基于历史录取数据生成参考志愿草稿");
            } else {
                dataStatus.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
                dataStatus.setDetail("目标年份官方招生计划、一分一段表或政策数据尚未全部就绪");
            }
            dataStatus.setReady(false);
            return dataStatus;
        }
        if (!item.isPolicyConfigured()) {
            dataStatus.setStatus("POLICY_MISSING");
            dataStatus.setDetail("政策配置未落库");
            dataStatus.setReady(false);
            return dataStatus;
        }
        if (item.getScoreLineCount() + item.getMajorScoreCount() <= 0) {
            dataStatus.setStatus("HISTORY_MISSING");
            dataStatus.setDetail("历史录取数据未检索到");
            dataStatus.setReady(false);
            return dataStatus;
        }
        if (item.getPlanCount() <= 0) {
            dataStatus.setStatus("PLAN_MISSING");
            dataStatus.setDetail("招生计划数据未检索到");
            dataStatus.setReady(false);
            return dataStatus;
        }
        dataStatus.setStatus("READY");
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            dataStatus.setDetail("2026 官方数据已导入并通过关键质检，模型重训完成前仅开放试推荐");
        } else {
            dataStatus.setDetail("政策、历史分数与计划数据均已检索到");
        }
        dataStatus.setReady(true);
        return dataStatus;
    }

    private String supportReason(BatchRuleRegistry.BatchRule rule, BatchSupportItem item, DataReadiness readiness, int year) {
        if (!rule.mainRankEngine()) {
            return rule.supportNote();
        }
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                    && supportsHistoricalReferenceRecommend(rule)
                    && historicalReferenceCount(item) > 0) {
                return AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
            }
            return admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())
                    ? AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING
                    : AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
        }
        if (!item.isPolicyConfigured()) {
            return "当前批次政策未落库，仅展示内置规则和数据缺口";
        }
        if (item.getScoreLineCount() + item.getMajorScoreCount() <= 0) {
            return "当前批次历史录取数据不足，仅开放查询说明";
        }
        if ("NORMAL_SPECIALTY".equals(rule.batchCode())) {
            if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
                return AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING;
            }
            if (item.getPlanCount() > 0 && item.getRequirementCount() > 0) {
                return "普通类高职专科批已检索到历史分数、招生计划和选科要求，支持完整推荐";
            }
            return "普通类高职专科批进入试推荐，需结合计划数据和官方材料复核";
        }
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            return AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING;
        }
        return "政策与历史数据门禁通过，支持普通类本科批完整推荐";
    }

    private List<String> warnings(BatchRuleRegistry.BatchRule rule, BatchSupportItem item, DataReadiness readiness, int year) {
        List<String> warnings = new ArrayList<>();
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            warnings.add(admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())
                    ? AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING
                    : AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        } else if (year == admissionYearService.getTargetYear() && rule.mainRankEngine() && !readiness.isMlTrainingReady()) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        }
        if (!item.isPolicyConfigured()) {
            warnings.add("政策配置未落库，使用内置支持矩阵展示");
        }
        if (historyCountForMissing(rule, item, readiness, year) <= 0) {
            warnings.add("未检索到该批次历史分数数据");
        }
        if (item.getPlanCount() <= 0) {
            warnings.add("未检索到该批次招生计划数据");
        }
        if (!rule.mainRankEngine()) {
            warnings.add(rule.supportNote());
        }
        return warnings;
    }

    private List<Integer> resolveDataSourceYears(DataReadiness readiness, List<Integer> trainingYears, int resolvedYear) {
        List<Integer> years = new ArrayList<>(trainingYears == null ? List.of() : trainingYears);
        if (resolvedYear == admissionYearService.getTargetYear()
                && readiness != null
                && admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                && readiness.isMlTrainingReady()
                && !years.contains(resolvedYear)) {
            years.add(resolvedYear);
        }
        return years;
    }

    private List<String> missingData(BatchRuleRegistry.BatchRule rule,
                                     BatchSupportItem item,
                                     DataReadiness readiness,
                                     int year) {
        List<String> missing = new ArrayList<>();
        if (!item.isPolicyConfigured()) {
            missing.add("policy_rule_config");
        }
        if (historyCountForMissing(rule, item, readiness, year) <= 0) {
            missing.add("data_score_line_gz/data_major_score_gz");
        }
        if (item.getPlanCount() <= 0) {
            missing.add("data_admission_plan_gz/admission_plan");
        }
        if (rule.mainRankEngine() && item.getRequirementCount() <= 0) {
            missing.add("data_major_requirement_gz");
        }
        if (rule.category() == BatchRuleRegistry.CandidateCategory.ART) {
            missing.add("art_composite_score_rules");
        }
        if (rule.category() == BatchRuleRegistry.CandidateCategory.SPORTS) {
            missing.add("sports_composite_score_rules");
        }
        if (rule.category() == BatchRuleRegistry.CandidateCategory.SPECIAL_PROGRAM) {
            missing.add("eligibility_audit_data");
        }
        return missing.stream().distinct().toList();
    }

    private boolean supportsHistoricalReferenceRecommend(BatchRuleRegistry.BatchRule rule) {
        if (rule == null) {
            return false;
        }
        return supportsHistoricalReferenceRecommendByCode(rule.batchCode());
    }

    private boolean supportsHistoricalReferenceRecommendByCode(String batchCode) {
        // v7.42：在 PRE_OFFICIAL_DATA 期间允许走「历史数据回退试推荐」的批次白名单。
        // - NORMAL_UNDERGRADUATE / NORMAL_SPECIALTY：96 志愿主链路。
        // - EARLY_C：本科提前批 C 段 60 志愿；2025 plan 78 + line 18 + major_score 238，
        //   面向公费师范 / 优师 / 免医 / 军警 / 定向类考生开放试推荐，等 2026 官方数据。
        return "NORMAL_UNDERGRADUATE".equals(batchCode)
                || "NORMAL_SPECIALTY".equals(batchCode)
                || "EARLY_C".equals(batchCode);
    }

    private long historicalReferenceCount(BatchSupportItem item) {
        if (item == null) {
            return 0;
        }
        return item.getHistoricalScoreLineCount() + item.getHistoricalMajorScoreCount();
    }

    private long historyCountForDisplay(BatchSupportItem item, DataReadiness readiness, int year) {
        if (item == null) {
            return 0;
        }
        if (year == admissionYearService.getTargetYear()
                && readiness != null
                && admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                && supportsHistoricalReferenceRecommendByCode(item.getBatchCode())) {
            return historicalReferenceCount(item);
        }
        return item.getScoreLineCount() + item.getMajorScoreCount();
    }

    private long historyCountForMissing(BatchRuleRegistry.BatchRule rule,
                                        BatchSupportItem item,
                                        DataReadiness readiness,
                                        int year) {
        if (item == null) {
            return 0;
        }
        if (year == admissionYearService.getTargetYear()
                && readiness != null
                && admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                && supportsHistoricalReferenceRecommend(rule)) {
            return historicalReferenceCount(item);
        }
        return item.getScoreLineCount() + item.getMajorScoreCount();
    }

    private Map<String, Long> buildSummary(List<BatchSupportItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String level : List.of("FULL_RECOMMEND", "TRIAL_RECOMMEND", "QUERY_ONLY", "UNSUPPORTED")) {
            summary.put(level, items.stream().filter(item -> level.equals(item.getSupportLevel())).count());
        }
        return summary;
    }

    private PolicySnapshot policySnapshot(String provinceCode, int year, BatchRuleRegistry.BatchRule rule) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT policy_status, max_volunteer_count, major_per_school_count, has_adjustment FROM policy_rule_config WHERE province = ? AND year = ? AND batch_code = ? AND candidate_type = ? AND enabled = 1 LIMIT 1",
                    provinceCode, year, rule.batchCode(), rule.candidateType());
            if (rows.isEmpty()) {
                return registryPolicySnapshot(rule);
            }
            Map<String, Object> row = rows.get(0);
            Object status = row.get("policy_status");
            return new PolicySnapshot(true,
                    status == null || status.toString().isBlank() ? "configured" : status.toString(),
                    toInt(row.get("max_volunteer_count")),
                    toInt(row.get("major_per_school_count")),
                    toInt(row.get("has_adjustment")) == 1);
        } catch (Exception ignored) {
            return registryPolicySnapshot(rule);
        }
    }

    private PolicySnapshot registryPolicySnapshot(BatchRuleRegistry.BatchRule rule) {
        return new PolicySnapshot(true, "registry_only", rule.targetCount(), 0, false);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long countRequirements(int year) {
        if (!tableExists("data_major_requirement_gz")) {
            return 0;
        }
        return queryLong("SELECT COUNT(*) FROM data_major_requirement_gz WHERE year = ?", year);
    }

    private long countPlanRows(int year, BatchRuleRegistry.BatchRule rule) {
        long total = 0;
        if (tableExists("data_admission_plan_gz")) {
            total += countByBatch("data_admission_plan_gz", year, rule);
        }
        if (tableExists("admission_plan")) {
            total += countByBatch("admission_plan", year, rule);
        }
        return total;
    }

    private long countByBatchAcrossYears(String table, List<Integer> years, BatchRuleRegistry.BatchRule rule) {
        if (years == null || years.isEmpty() || !tableExists(table)) {
            return 0;
        }
        String batchColumn = batchColumn(table);
        if (batchColumn.isBlank()) {
            return 0;
        }
        List<Integer> distinctYears = years.stream()
                .filter(year -> year != null && year > 0)
                .distinct()
                .toList();
        if (distinctYears.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctYears.size(), "?"));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " + batchColumn + " AS data_batch, COUNT(*) AS cnt FROM " + table
                            + " WHERE year IN (" + placeholders + ") GROUP BY " + batchColumn,
                    distinctYears.toArray());
            long total = 0;
            for (Map<String, Object> row : rows) {
                Object batch = row.get("data_batch");
                if (!BatchRuleRegistry.batchMatches(rule.batchCode(), batch == null ? "" : batch.toString())) {
                    continue;
                }
                Object count = row.get("cnt");
                if (count instanceof Number number) {
                    total += number.longValue();
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long countByBatch(String table, int year, BatchRuleRegistry.BatchRule rule) {
        if (!tableExists(table)) {
            return 0;
        }
        String batchColumn = batchColumn(table);
        if (batchColumn.isBlank()) {
            return 0;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " + batchColumn + " AS data_batch, COUNT(*) AS cnt FROM " + table + " WHERE year = ? GROUP BY " + batchColumn, year);
            long total = 0;
            for (Map<String, Object> row : rows) {
                Object batch = row.get("data_batch");
                if (!BatchRuleRegistry.batchMatches(rule.batchCode(), batch == null ? "" : batch.toString())) {
                    continue;
                }
                Object count = row.get("cnt");
                if (count instanceof Number number) {
                    total += number.longValue();
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String batchColumn(String table) {
        if (columnExists(table, "batch_code")) {
            return "batch_code";
        }
        if (columnExists(table, "batch")) {
            return "batch";
        }
        return "";
    }

    private boolean columnExists(String table, String column) {
        return queryLong("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?", table, column) > 0;
    }

    private boolean tableExists(String table) {
        return queryLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?", table) > 0;
    }

    private long queryLong(String sql, Object... args) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
            return value == null ? 0 : value;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private record PolicySnapshot(boolean configured, String status, int maxVolunteerCount,
                                  int majorPerSchoolCount, boolean hasAdjustment) {
    }

    @Data
    public static class BatchSupportResponse {
        private String provinceCode;
        private int year;
        private int activeAdmissionYear;
        private int latestOfficialDataYear;
        private int targetYear;
        private int futureImportYear;
        private List<Integer> historyYears = List.of();
        private List<Integer> trainingYears = List.of();
        private List<Integer> dataSourceYears = List.of();
        private String recommendationPhase;
        private String capabilityStatus = "";
        private boolean estimateMode;
        private boolean officialDataReady;
        private DataReadiness dataReadiness;
        private boolean publicYearLocked;
        private List<BatchSupportItem> items = List.of();
        private Map<String, Long> summary = Map.of();
    }

    @Data
    public static class BatchSupportItem {
        private String batchCode;
        private String batchName;
        private String candidateType;
        private String category;
        private String supportLevel;
        private String recommendMode;
        private String engine;
        private String engineName;
        private int targetCount;
        private int maxVolunteerCount;
        private int majorPerSchoolCount;
        private boolean hasAdjustment;
        private String volunteerMode;
        private boolean policyConfigured;
        private String policyStatus;
        private long scoreLineCount;
        private long majorScoreCount;
        private long historicalScoreLineCount;
        private long historicalMajorScoreCount;
        private long planCount;
        private long requirementCount;
        private DataStatus dataStatus;
        private List<String> missingData = List.of();
        private String supportNote;
        private String supportReason;
        private List<String> warnings = List.of();
    }

    @Data
    public static class DataReadiness {
        private String provinceCode = "GZ";
        private int year;
        private boolean policyReady;
        private boolean scoreSegmentReady;
        private boolean admissionPlanReady;
        private boolean majorRequirementReady;
        private boolean majorMetaReady;
        private boolean mlTrainingReady;
        private boolean historicalTrainingReady;
        private String recommendationPhase = AdmissionYearService.PHASE_PRE_OFFICIAL_DATA;
        private String latestImportBatchId = "";
        private String lastCheckedAt = "";
        private String remarks = "";
    }

    @Data
    public static class DataStatus {
        private String status;
        private long policyCount;
        private long scoreLineCount;
        private long majorScoreCount;
        private long historyCount;
        private long planCount;
        private long requirementCount;
        private boolean ready;
        private String detail;
    }
}
