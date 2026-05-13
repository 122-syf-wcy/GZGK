package com.gzly.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchSupportService {

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;

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
        BatchSupportResponse response = new BatchSupportResponse();
        response.setProvinceCode(provinceCode == null || provinceCode.isBlank() ? "GZ" : provinceCode.trim());
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
            items.add(buildItem(rule, response.getProvinceCode(), resolvedYear, readiness));
        }
        response.setItems(items);
        response.setSummary(buildSummary(items));
        return response;
    }

    private BatchSupportItem buildItem(BatchRuleRegistry.BatchRule rule, String provinceCode, int year, DataReadiness readiness) {
        PolicySnapshot policy = policySnapshot(provinceCode, year, rule.batchCode(), rule.candidateType());
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
        item.setRequirementCount(countRequirements(year));
        item.setPlanCount(countPlanRows(year, rule));
        item.setSupportLevel(resolveSupportLevel(rule, item, readiness, year));
        item.setDataStatus(dataStatus(item, readiness, year));
        item.setMissingData(missingData(rule, item));
        item.setSupportReason(supportReason(rule, item, readiness, year));
        item.setWarnings(warnings(rule, item, readiness, year));
        return item;
    }

    private String resolveSupportLevel(BatchRuleRegistry.BatchRule rule, BatchSupportItem item, DataReadiness readiness, int year) {
        if (!rule.mainRankEngine()) {
            // 非主链路批次（提前批/艺/体/专项）始终保持 baseSupportLevel(=QUERY_ONLY)，
            // 即便 PRE_OFFICIAL_DATA 阶段开放普通本/专科预估，这类批次也不允许预估。
            return rule.baseSupportLevel().name();
        }
        boolean targetYear = year == admissionYearService.getTargetYear();
        boolean officialReady = admissionYearService.isOfficialDataReady(readiness);
        boolean hasHistory = item.getScoreLineCount() + item.getMajorScoreCount() > 0;
        boolean isOrdinary = rule.category() == BatchRuleRegistry.CandidateCategory.ORDINARY;
        if (targetYear && !officialReady) {
            // PRE_OFFICIAL_DATA 阶段：仅普通本/专科 + 政策已配 + 历史训练就绪时开放 ESTIMATE_RECOMMEND，
            // 不再要求 year=targetYear 的 score_line/major_score 行数 — 目标年份的官方数据本来就为空，
            // 历史数据可用性由 readiness.historicalTrainingReady 这一管理侧 flag 统一表达，
            // 与 VolunteerRecommendController.resolveSupportLevel 在 /recommend 路径上的判定口径保持一致。
            // 其余（含 OFFICIAL_DATA_PARTIAL）仍走 QUERY_ONLY。
            if (isOrdinary
                    && item.isPolicyConfigured()
                    && readiness != null
                    && readiness.isHistoricalTrainingReady()
                    && admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase())) {
                return BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name();
            }
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (!item.isPolicyConfigured() || !hasHistory) {
            return BatchRuleRegistry.SupportLevel.QUERY_ONLY.name();
        }
        if (targetYear && !readiness.isMlTrainingReady()) {
            return BatchRuleRegistry.SupportLevel.TRIAL_RECOMMEND.name();
        }
        if (targetYear
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
        dataStatus.setHistoryCount(item.getScoreLineCount() + item.getMajorScoreCount());
        dataStatus.setPlanCount(item.getPlanCount());
        dataStatus.setRequirementCount(item.getRequirementCount());
        if (year == admissionYearService.getTargetYear() && !admissionYearService.isOfficialDataReady(readiness)) {
            if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
                dataStatus.setStatus(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
                dataStatus.setDetail("目标年份官方数据正在分批导入和质检，关键数据尚未全部就绪");
            } else if (BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name().equals(item.getSupportLevel())) {
                dataStatus.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
                dataStatus.setDetail("2026 官方数据未发布，基于 2024/2025 历史数据提供预估参考");
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
            if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
                return AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING;
            }
            if (BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name().equals(item.getSupportLevel())) {
                return AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING;
            }
            return AdmissionYearService.PRE_OFFICIAL_DATA_WARNING;
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
            if (admissionYearService.isOfficialDataPartialPhase(readiness.getRecommendationPhase())) {
                warnings.add(AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING);
            } else if (BatchRuleRegistry.SupportLevel.ESTIMATE_RECOMMEND.name().equals(item.getSupportLevel())) {
                warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
            } else {
                warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
            }
        } else if (year == admissionYearService.getTargetYear() && rule.mainRankEngine() && !readiness.isMlTrainingReady()) {
            warnings.add(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        }
        if (!item.isPolicyConfigured()) {
            warnings.add("政策配置未落库，使用内置支持矩阵展示");
        }
        if (item.getScoreLineCount() + item.getMajorScoreCount() <= 0) {
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

    private List<String> missingData(BatchRuleRegistry.BatchRule rule, BatchSupportItem item) {
        List<String> missing = new ArrayList<>();
        if (!item.isPolicyConfigured()) {
            missing.add("policy_rule_config");
        }
        if (item.getScoreLineCount() + item.getMajorScoreCount() <= 0) {
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

    private Map<String, Long> buildSummary(List<BatchSupportItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String level : List.of("FULL_RECOMMEND", "ESTIMATE_RECOMMEND", "TRIAL_RECOMMEND", "QUERY_ONLY", "UNSUPPORTED")) {
            summary.put(level, items.stream().filter(item -> level.equals(item.getSupportLevel())).count());
        }
        return summary;
    }

    private PolicySnapshot policySnapshot(String provinceCode, int year, String batchCode, String candidateType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT policy_status, max_volunteer_count, major_per_school_count, has_adjustment FROM policy_rule_config WHERE province = ? AND year = ? AND batch_code = ? AND candidate_type = ? AND enabled = 1 LIMIT 1",
                    provinceCode, year, batchCode, candidateType);
            if (rows.isEmpty()) {
                return new PolicySnapshot(false, "registry_only", 0, 0, false);
            }
            Map<String, Object> row = rows.get(0);
            Object status = row.get("policy_status");
            return new PolicySnapshot(true,
                    status == null || status.toString().isBlank() ? "configured" : status.toString(),
                    toInt(row.get("max_volunteer_count")),
                    toInt(row.get("major_per_school_count")),
                    toInt(row.get("has_adjustment")) == 1);
        } catch (Exception ignored) {
            return new PolicySnapshot(false, "registry_only", 0, 0, false);
        }
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
