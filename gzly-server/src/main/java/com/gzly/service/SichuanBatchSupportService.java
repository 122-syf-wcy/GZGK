package com.gzly.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

/**
 * 四川（以及湖北 / 安徽等 PROFESSIONAL_GROUP_45 省份）的批次支持矩阵服务。
 *
 * <p>与 {@link BatchSupportService} 的差异：</p>
 * <ul>
 *   <li>规则来自 {@link SichuanBatchRuleRegistry}（18 批次 SC_* 前缀），而非贵州 18 批次。</li>
 *   <li>历史录取数据从 {@code data_admission_group_line} / {@code data_admission_group_plan}
 *       的「按 province_code + year + 批次关键词 LIKE」聚合，而非贵州专用的
 *       {@code data_score_line_gz} / {@code data_major_score_gz}。</li>
 *   <li>主流程批次（{@code SC_BENKE_B} / {@code SC_ZHUANKE_B}）按数据可用度 + readiness 阶段
 *       动态计算 {@code TRIAL_RECOMMEND} / {@code FULL_RECOMMEND}；其余 16 批次先按 {@code QUERY_ONLY}
 *       兜底，等综合分公式 / 顺序志愿引擎逐步落地后再放开。</li>
 * </ul>
 *
 * <p>为了与现有前端 / VolunteerRecommendController 完全兼容，所有返回类型复用
 * {@link BatchSupportService.BatchSupportResponse} / {@link BatchSupportService.BatchSupportItem}
 * 等数据结构，前端无需感知数据源差异。</p>
 */
@Service
@Slf4j
public class SichuanBatchSupportService {

    private static final Duration BATCH_SUPPORT_CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;

    private final Cache<String, BatchSupportService.BatchSupportResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(BATCH_SUPPORT_CACHE_TTL)
            .maximumSize(128)
            .recordStats()
            .build();

    @Autowired
    public SichuanBatchSupportService(JdbcTemplate jdbcTemplate,
                                       AdmissionYearService admissionYearService,
                                       DataYearReadinessService dataYearReadinessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
        this.dataYearReadinessService = dataYearReadinessService;
    }

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year) {
        return supportMatrix(provinceCode, year, false);
    }

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year, boolean publicYearLocked) {
        int resolvedYear = year == null || year <= 0 ? admissionYearService.getActiveAdmissionYear() : year;
        String normalizedProvince = provinceCode == null || provinceCode.isBlank()
                ? ProvincePolicyService.SC : provinceCode.trim().toUpperCase(Locale.ROOT);
        String cacheKey = normalizedProvince + ":" + resolvedYear + ":" + publicYearLocked;
        try {
            return cache.get(cacheKey, key -> doSupportMatrix(normalizedProvince, resolvedYear, publicYearLocked));
        } catch (RuntimeException e) {
            log.warn("sichuanBatchSupportCache 读取失败，回源直查 key={}", cacheKey, e);
            return doSupportMatrix(normalizedProvince, resolvedYear, publicYearLocked);
        }
    }

    public void invalidateSupportMatrixCache() {
        cache.invalidateAll();
    }

    private BatchSupportService.BatchSupportResponse doSupportMatrix(String provinceCode, int resolvedYear,
                                                                     boolean publicYearLocked) {
        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setProvinceCode(provinceCode);
        response.setYear(resolvedYear);
        response.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        response.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        response.setHistoryYears(admissionYearService.resolveHistoryYears(admissionYearService.getActiveAdmissionYear()));
        response.setTrainingYears(admissionYearService.resolveTrainingYears());
        response.setTargetYear(admissionYearService.getTargetYear());
        response.setFutureImportYear(admissionYearService.getFutureImportYear());
        response.setPublicYearLocked(publicYearLocked);

        BatchSupportService.DataReadiness readiness = dataYearReadinessService.getOrDefaultReadiness(provinceCode, resolvedYear);
        response.setDataReadiness(readiness);
        response.setRecommendationPhase(readiness.getRecommendationPhase());
        response.setOfficialDataReady(admissionYearService.isOfficialDataReady(readiness));
        response.setEstimateMode(admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase()));
        response.setDataSourceYears(resolveDataSourceYears(readiness, response.getTrainingYears(), resolvedYear));

        List<BatchSupportService.BatchSupportItem> items = new ArrayList<>();
        for (SichuanBatchRuleRegistry.BatchRule rule : SichuanBatchRuleRegistry.allRules()) {
            items.add(buildItem(rule, provinceCode, resolvedYear, readiness, response.getDataSourceYears()));
        }
        response.setItems(items);
        response.setSummary(buildSummary(items));
        return response;
    }

    private BatchSupportService.BatchSupportItem buildItem(SichuanBatchRuleRegistry.BatchRule rule,
                                                           String provinceCode,
                                                           int year,
                                                           BatchSupportService.DataReadiness readiness,
                                                           List<Integer> dataSourceYears) {
        PolicySnapshot policy = policySnapshot(provinceCode, year, rule);
        BatchSupportService.BatchSupportItem item = new BatchSupportService.BatchSupportItem();
        item.setBatchCode(rule.batchCode());
        item.setBatchName(rule.batchName());
        item.setCandidateType(rule.candidateType());
        item.setCategory(rule.category().name());
        item.setRecommendMode(rule.recommendMode().name());
        item.setEngine(engineNameFor(rule));
        item.setEngineName(engineNameFor(rule));
        item.setTargetCount(rule.targetCount());
        item.setMaxVolunteerCount(policy.maxVolunteerCount() > 0 ? policy.maxVolunteerCount() : rule.targetCount());
        item.setMajorPerSchoolCount(rule.majorsPerGroup());
        item.setHasAdjustment(rule.hasAdjustment());
        item.setVolunteerMode(rule.volunteerMode());
        item.setSupportNote(rule.supportNote());
        item.setPolicyConfigured(policy.configured());
        item.setPolicyStatus(policy.status());

        long currentLine = countGroupLineByBatch(provinceCode, year, rule);
        long historicalLine = countGroupLineByBatchAcrossYears(provinceCode, dataSourceYears, rule);
        long planRows = countGroupPlanByBatch(provinceCode, year, rule);
        long requirementRows = countRequirements(provinceCode, year);

        item.setScoreLineCount(currentLine);
        item.setMajorScoreCount(0L);
        item.setHistoricalScoreLineCount(historicalLine);
        item.setHistoricalMajorScoreCount(0L);
        item.setRequirementCount(requirementRows);
        item.setPlanCount(planRows);
        item.setSupportLevel(resolveSupportLevel(rule, item, readiness, year));
        item.setDataStatus(dataStatus(item, readiness, year));
        item.setMissingData(missingData(rule, item, readiness, year));
        item.setSupportReason(supportReason(rule, item, readiness, year));
        item.setWarnings(warnings(rule, item, readiness, year));
        return item;
    }

    /**
     * 主流程批次（{@code mainRankEngine && PARALLEL_GROUP && ORDINARY}）按贵州相同的口径分级：
     * <ul>
     *   <li>2026 官方数据未发布 + 历史回退数据 &gt; 0 → {@code TRIAL_RECOMMEND}</li>
     *   <li>2026 官方数据未发布 + 无历史 → {@code QUERY_ONLY}</li>
     *   <li>2026 数据已导入但 ML 未重训 → {@code TRIAL_RECOMMEND}</li>
     *   <li>2026 model_retrained + plan/requirement 齐 → {@code FULL_RECOMMEND}</li>
     * </ul>
     * 其它批次（艺术 / 体育 / 专项 / 顺序志愿）暂走 {@code QUERY_ONLY}，等专项 engine 落地后再升级。
     */
    private String resolveSupportLevel(SichuanBatchRuleRegistry.BatchRule rule,
                                       BatchSupportService.BatchSupportItem item,
                                       BatchSupportService.DataReadiness readiness,
                                       int year) {
        if (!isMainRecommendablePipeline(rule)) {
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                    && item.isPolicyConfigured()
                    && historicalReferenceCount(item) > 0) {
                return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
            }
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        boolean hasHistory = item.getScoreLineCount() > 0;
        if (!item.isPolicyConfigured() || !hasHistory) {
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        if (year == admissionYearService.getTargetYear()
                && (!admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                || !readiness.isMajorMetaReady()
                || item.getPlanCount() <= 0)) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        return BatchRuleRegistry.SupportLevel.FULL_RECOMMEND.name();
    }

    private BatchSupportService.DataStatus dataStatus(BatchSupportService.BatchSupportItem item,
                                                      BatchSupportService.DataReadiness readiness,
                                                      int year) {
        BatchSupportService.DataStatus status = new BatchSupportService.DataStatus();
        status.setPolicyCount(item.isPolicyConfigured() ? 1 : 0);
        status.setScoreLineCount(item.getScoreLineCount());
        status.setMajorScoreCount(item.getMajorScoreCount());
        status.setHistoryCount(historyCountForDisplay(item, readiness, year));
        status.setPlanCount(item.getPlanCount());
        status.setRequirementCount(item.getRequirementCount());
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
                status.setStatus(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
                status.setDetail("目标年份官方数据正在分批导入和质检，关键数据尚未全部就绪");
            } else if (isMainRecommendablePipelineByCode(item.getBatchCode()) && historicalReferenceCount(item) > 0) {
                status.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
                status.setDetail("目标年份官方数据尚未发布；当前可基于历史录取数据生成参考志愿草稿");
            } else {
                status.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
                status.setDetail("目标年份官方招生计划、一分一段表或政策数据尚未全部就绪");
            }
            status.setReady(false);
            return status;
        }
        if (!item.isPolicyConfigured()) {
            status.setStatus("POLICY_MISSING");
            status.setDetail("政策配置未落库");
            status.setReady(false);
            return status;
        }
        if (item.getScoreLineCount() <= 0) {
            status.setStatus("HISTORY_MISSING");
            status.setDetail("院校专业组历史调档线未检索到");
            status.setReady(false);
            return status;
        }
        if (item.getPlanCount() <= 0) {
            status.setStatus("PLAN_MISSING");
            status.setDetail("院校专业组招生计划未检索到");
            status.setReady(false);
            return status;
        }
        status.setStatus("READY");
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            status.setDetail("2026 官方数据已导入并通过关键质检，模型重训完成前仅开放试推荐");
        } else {
            status.setDetail("政策、历史院校专业组分数与招生计划数据均已检索到");
        }
        status.setReady(true);
        return status;
    }

    private String supportReason(SichuanBatchRuleRegistry.BatchRule rule,
                                 BatchSupportService.BatchSupportItem item,
                                 BatchSupportService.DataReadiness readiness,
                                 int year) {
        if (!isMainRecommendablePipeline(rule)) {
            return rule.supportNote();
        }
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
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
        if (item.getScoreLineCount() <= 0) {
            return "当前批次院校专业组历史调档线不足，仅开放查询说明";
        }
        if (year == admissionYearService.getTargetYear() && !readiness.isMlTrainingReady()) {
            return AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING;
        }
        return "政策、院校专业组历史调档线与招生计划齐备，支持四川院校专业组完整推荐";
    }

    private List<String> warnings(SichuanBatchRuleRegistry.BatchRule rule,
                                  BatchSupportService.BatchSupportItem item,
                                  BatchSupportService.DataReadiness readiness,
                                  int year) {
        List<String> warnings = new ArrayList<>();
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            warnings.add(admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())
                    ? AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING
                    : AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        } else if (year == admissionYearService.getTargetYear()
                && isMainRecommendablePipeline(rule)
                && !readiness.isMlTrainingReady()) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        }
        if (!item.isPolicyConfigured()) {
            warnings.add("政策配置未落库，使用内置支持矩阵展示");
        }
        if (historyCountForMissing(rule, item, readiness, year) <= 0) {
            warnings.add("未检索到该批次院校专业组历史数据");
        }
        if (item.getPlanCount() <= 0) {
            warnings.add("未检索到该批次院校专业组招生计划数据");
        }
        if (rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ART
                || rule.category() == SichuanBatchRuleRegistry.CandidateCategory.SPORTS) {
            warnings.add("艺术 / 体育按综合分平行志愿排序，综合分公式与统考成绩需考生本人手动核对");
        }
        if (rule.category() == SichuanBatchRuleRegistry.CandidateCategory.SPECIAL_PROGRAM) {
            warnings.add("专项 / 民族预科 / 高校专项需户籍 / 学籍 / 报名审核结果，仅展示规则与资格清单");
        }
        if (!isMainRecommendablePipeline(rule)) {
            warnings.add(rule.supportNote());
        }
        return warnings.stream().distinct().toList();
    }

    private List<String> missingData(SichuanBatchRuleRegistry.BatchRule rule,
                                     BatchSupportService.BatchSupportItem item,
                                     BatchSupportService.DataReadiness readiness,
                                     int year) {
        List<String> missing = new ArrayList<>();
        if (!item.isPolicyConfigured()) {
            missing.add("policy_rule_config");
        }
        if (historyCountForMissing(rule, item, readiness, year) <= 0) {
            missing.add("data_admission_group_line");
        }
        if (item.getPlanCount() <= 0) {
            missing.add("data_admission_group_plan");
        }
        if (isMainRecommendablePipeline(rule) && item.getRequirementCount() <= 0) {
            missing.add("data_major_requirement");
        }
        if (rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ART) {
            missing.add("art_composite_score_rules");
        }
        if (rule.category() == SichuanBatchRuleRegistry.CandidateCategory.SPORTS) {
            missing.add("sports_composite_score_rules");
        }
        if (rule.category() == SichuanBatchRuleRegistry.CandidateCategory.SPECIAL_PROGRAM) {
            missing.add("eligibility_audit_data");
        }
        return missing.stream().distinct().toList();
    }

    private boolean isMainRecommendablePipeline(SichuanBatchRuleRegistry.BatchRule rule) {
        if (rule == null) {
            return false;
        }
        return rule.mainRankEngine()
                && rule.recommendMode() == SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                && rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ORDINARY;
    }

    private boolean isMainRecommendablePipelineByCode(String batchCode) {
        return SichuanBatchRuleRegistry.find(batchCode)
                .map(this::isMainRecommendablePipeline)
                .orElse(false);
    }

    private long historicalReferenceCount(BatchSupportService.BatchSupportItem item) {
        if (item == null) {
            return 0;
        }
        return item.getHistoricalScoreLineCount();
    }

    private long historyCountForDisplay(BatchSupportService.BatchSupportItem item,
                                        BatchSupportService.DataReadiness readiness,
                                        int year) {
        if (item == null) {
            return 0;
        }
        if (year == admissionYearService.getTargetYear()
                && readiness != null
                && admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                && isMainRecommendablePipelineByCode(item.getBatchCode())) {
            return historicalReferenceCount(item);
        }
        return item.getScoreLineCount();
    }

    private long historyCountForMissing(SichuanBatchRuleRegistry.BatchRule rule,
                                        BatchSupportService.BatchSupportItem item,
                                        BatchSupportService.DataReadiness readiness,
                                        int year) {
        if (item == null) {
            return 0;
        }
        if (year == admissionYearService.getTargetYear()
                && readiness != null
                && admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())
                && isMainRecommendablePipeline(rule)) {
            return historicalReferenceCount(item);
        }
        return item.getScoreLineCount();
    }

    private Map<String, Long> buildSummary(List<BatchSupportService.BatchSupportItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String level : List.of("FULL_RECOMMEND", "TRIAL_RECOMMEND", "QUERY_ONLY", "UNSUPPORTED")) {
            summary.put(level, items.stream().filter(item -> level.equals(item.getSupportLevel())).count());
        }
        return summary;
    }

    private List<Integer> resolveDataSourceYears(BatchSupportService.DataReadiness readiness,
                                                 List<Integer> trainingYears,
                                                 int resolvedYear) {
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

    private String engineNameFor(SichuanBatchRuleRegistry.BatchRule rule) {
        if (isMainRecommendablePipeline(rule)) {
            return "ProfessionalGroupVolunteerEngine";
        }
        return switch (rule.category()) {
            case ART -> "SichuanArtCompositeEngine";
            case SPORTS -> "SichuanSportsCompositeEngine";
            case SPECIAL_PROGRAM -> "SichuanSpecialPlanEligibilityEngine";
            case EARLY -> rule.recommendMode() == SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                    ? "ProfessionalGroupVolunteerEngine"
                    : "SichuanSequentialCollegeEngine";
            case OTHER -> "SichuanSequentialCollegeEngine";
            default -> "ProfessionalGroupVolunteerEngine";
        };
    }

    // ---------- 数据计数（按 SichuanBatchRuleRegistry.batchKeywords 在 group_line / group_plan LIKE 匹配） ----------

    private long countGroupLineByBatch(String provinceCode, int year, SichuanBatchRuleRegistry.BatchRule rule) {
        if (!tableExists("data_admission_group_line")) {
            return 0;
        }
        List<String> keywords = effectiveKeywords(rule);
        if (keywords.isEmpty()) {
            return 0;
        }
        return countByKeywords("data_admission_group_line", provinceCode, List.of(year), keywords, rule);
    }

    private long countGroupLineByBatchAcrossYears(String provinceCode,
                                                  List<Integer> years,
                                                  SichuanBatchRuleRegistry.BatchRule rule) {
        if (years == null || years.isEmpty() || !tableExists("data_admission_group_line")) {
            return 0;
        }
        List<String> keywords = effectiveKeywords(rule);
        if (keywords.isEmpty()) {
            return 0;
        }
        return countByKeywords("data_admission_group_line", provinceCode, years, keywords, rule);
    }

    private long countGroupPlanByBatch(String provinceCode, int year, SichuanBatchRuleRegistry.BatchRule rule) {
        if (!tableExists("data_admission_group_plan")) {
            return 0;
        }
        List<String> keywords = effectiveKeywords(rule);
        if (keywords.isEmpty()) {
            return 0;
        }
        return countByKeywords("data_admission_group_plan", provinceCode, List.of(year), keywords, rule);
    }

    /**
     * data_admission_group_line / data_admission_group_plan 的 batch 字段是中文（如 "本科批 B 段"），
     * 这里按 SichuanBatchRuleRegistry 的 batchKeywords（包含 batchName + aliases）做 LIKE 匹配。
     * 多个关键词以 OR 组合，但用 distinct 计数避免重复。
     */
    private long countByKeywords(String table, String provinceCode, List<Integer> years,
                                 List<String> keywords, SichuanBatchRuleRegistry.BatchRule rule) {
        if (years == null || years.isEmpty() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        List<Integer> distinctYears = years.stream()
                .filter(year -> year != null && year > 0)
                .distinct()
                .toList();
        if (distinctYears.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT CONCAT(school_id, '#', COALESCE(group_code, ''), '#', COALESCE(subject_type, ''))) FROM ")
                .append(table)
                .append(" WHERE province_code = ? AND year IN (")
                .append(String.join(",", java.util.Collections.nCopies(distinctYears.size(), "?")))
                .append(") AND (");
        List<Object> args = new ArrayList<>();
        args.add(provinceCode);
        args.addAll(distinctYears);
        boolean first = true;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (!first) {
                sql.append(" OR ");
            }
            sql.append("batch LIKE ?");
            args.add("%" + keyword + "%");
            first = false;
        }
        sql.append(")");
        if (first) {
            return 0;
        }
        return queryLong(sql.toString(), args.toArray());
    }

    private List<String> effectiveKeywords(SichuanBatchRuleRegistry.BatchRule rule) {
        if (rule == null) {
            return List.of();
        }
        List<String> base = new ArrayList<>(SichuanBatchRuleRegistry.batchKeywords(rule.batchCode()));
        // 加入批次名核心词，便于匹配只有"本科批"/"专科批"字样的简化记录
        if (rule.recommendMode() == SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP
                && rule.category() == SichuanBatchRuleRegistry.CandidateCategory.ORDINARY) {
            if ("SC_BENKE_B".equals(rule.batchCode())) {
                base.add("本科批");
            }
            if ("SC_ZHUANKE_B".equals(rule.batchCode())) {
                base.add("高职专科批");
                base.add("专科批");
            }
        }
        return base.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private long countRequirements(String provinceCode, int year) {
        if (!tableExists("data_major_requirement")) {
            return 0;
        }
        if (columnExists("data_major_requirement", "province_code")) {
            return queryLong("SELECT COUNT(*) FROM data_major_requirement WHERE province_code = ? AND year = ?", provinceCode, year);
        }
        return queryLong("SELECT COUNT(*) FROM data_major_requirement WHERE year = ?", year);
    }

    private PolicySnapshot policySnapshot(String provinceCode, int year, SichuanBatchRuleRegistry.BatchRule rule) {
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

    private PolicySnapshot registryPolicySnapshot(SichuanBatchRuleRegistry.BatchRule rule) {
        return new PolicySnapshot(true, "registry_only", rule.targetCount(), rule.majorsPerGroup(), rule.hasAdjustment());
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

    private boolean tableExists(String table) {
        return queryLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?", table) > 0;
    }

    private boolean columnExists(String table, String column) {
        return queryLong("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?", table, column) > 0;
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
}
