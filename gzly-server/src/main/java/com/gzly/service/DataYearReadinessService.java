package com.gzly.service;

import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DataYearReadinessService {

    /** policy_ready / score_segment_ready / ... 触发阈值: 表至少有多少行才算"就绪". */
    static final int POLICY_MIN_ROWS = 1;
    static final int SCORE_RANK_MIN_ROWS = 200;
    static final int ADMISSION_PLAN_MIN_ROWS = 1000;
    static final int MAJOR_REQUIREMENT_MIN_ROWS = 1000;
    static final int MAJOR_META_MIN_ROWS = 1000;
    static final int HISTORICAL_TRAINING_MIN_ROWS = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionYearService admissionYearService;

    public DataYearReadinessService(JdbcTemplate jdbcTemplate, AdmissionYearService admissionYearService) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionYearService = admissionYearService;
    }

    public Optional<BatchSupportService.DataReadiness> getReadiness(String provinceCode, int year) {
        String normalizedProvince = normalizeProvinceCode(provinceCode);
        if (!tableExists("data_year_readiness")) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT policy_ready, score_segment_ready, admission_plan_ready, major_requirement_ready, major_meta_ready, ml_training_ready, historical_training_ready, recommendation_phase, latest_import_batch_id, last_checked_at, remarks FROM data_year_readiness WHERE province_code = ? AND year = ? ORDER BY last_checked_at DESC, id DESC LIMIT 1",
                    normalizedProvince, year);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(readinessFromRow(rows.get(0), normalizedProvince, year));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public BatchSupportService.DataReadiness getOrDefaultReadiness(String provinceCode, int year) {
        String normalizedProvince = normalizeProvinceCode(provinceCode);
        return getReadiness(normalizedProvince, year).orElseGet(() -> defaultReadiness(normalizedProvince, year));
    }

    public boolean isOfficialDataReady(String provinceCode, int year) {
        return admissionYearService.isOfficialDataReady(getOrDefaultReadiness(provinceCode, year));
    }

    public boolean isModelRetrained(String provinceCode, int year) {
        BatchSupportService.DataReadiness readiness = getOrDefaultReadiness(provinceCode, year);
        return admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                && readiness.isMlTrainingReady()
                && readiness.isMajorMetaReady();
    }

    /**
     * 按实际表行数校准 readiness flags, 仅落库, 不改 recommendation_phase.
     * 只支持 GZ 板块的几张专用表 (data_score_rank_gz / data_major_requirement_gz / ...).
     * 返回 校准前 vs 校准后 的对照, 方便 admin UI 显示。
     */
    public RefreshResult refreshReadinessFlags(String provinceCode, int year) {
        String normalizedProvince = normalizeProvinceCode(provinceCode);
        if (!"GZ".equals(normalizedProvince)) {
            throw new com.gzly.common.exception.BizException(400,
                    "只支持 GZ 板块自动校准, 其他省份请走 admin import job 流程");
        }
        if (year <= 0 || year > 2999) {
            throw new com.gzly.common.exception.BizException(400, "year 不合法: " + year);
        }
        if (!tableExists("data_year_readiness")) {
            throw new com.gzly.common.exception.BizException(500, "data_year_readiness 表不存在");
        }

        Map<String, Boolean> before = currentFlagsRow(normalizedProvince, year);
        Map<String, Long> rowCounts = sampleGzRowCounts(year);
        Map<String, Boolean> after = new LinkedHashMap<>();
        after.put("policy_ready", rowCounts.getOrDefault("policy_rule", 0L) >= POLICY_MIN_ROWS);
        after.put("score_segment_ready", rowCounts.getOrDefault("score_rank_gz", 0L) >= SCORE_RANK_MIN_ROWS);
        after.put("admission_plan_ready", rowCounts.getOrDefault("admission_plan_gz", 0L) >= ADMISSION_PLAN_MIN_ROWS);
        after.put("major_requirement_ready", rowCounts.getOrDefault("major_requirement_gz", 0L) >= MAJOR_REQUIREMENT_MIN_ROWS);
        after.put("major_meta_ready", rowCounts.getOrDefault("major_meta_gz", 0L) >= MAJOR_META_MIN_ROWS);
        after.put("historical_training_ready", rowCounts.getOrDefault("history_major_score_gz", 0L) >= HISTORICAL_TRAINING_MIN_ROWS);

        // ml_training_ready 不在这里自动翻; 模型何时切换由 ML pipeline 自行控制
        Boolean keepMl = before == null ? null : before.get("ml_training_ready");

        String remarks = "readiness 自动校准 @ " + LocalDateTime.now()
                + " (rowCounts=" + rowCounts + ")";
        upsertReadiness(normalizedProvince, year, after, keepMl, remarks);

        return new RefreshResult(normalizedProvince, year, before, after, rowCounts, remarks);
    }

    private Map<String, Long> sampleGzRowCounts(int year) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("policy_rule", queryLong(
                "SELECT COUNT(*) FROM policy_rule_config WHERE province IN ('贵州','GZ') AND year = ? AND enabled = 1",
                year));
        counts.put("score_rank_gz", queryLong(
                "SELECT COUNT(*) FROM data_score_rank_gz WHERE year = ?", year));
        counts.put("admission_plan_gz", queryLong(
                "SELECT COUNT(*) FROM data_admission_plan_gz WHERE year = ?", year));
        counts.put("major_requirement_gz", queryLong(
                "SELECT COUNT(*) FROM data_major_requirement_gz WHERE year = ?", year));
        counts.put("major_meta_gz", queryLong(
                "SELECT COUNT(*) FROM data_major_meta_gz WHERE year = ?", year));
        counts.put("history_major_score_gz", queryLong(
                "SELECT COUNT(*) FROM data_major_score_gz WHERE year < ?", year));
        return counts;
    }

    private Map<String, Boolean> currentFlagsRow(String provinceCode, int year) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT policy_ready, score_segment_ready, admission_plan_ready, major_requirement_ready, major_meta_ready, ml_training_ready, historical_training_ready FROM data_year_readiness WHERE province_code = ? AND year = ? ORDER BY id DESC LIMIT 1",
                    provinceCode, year);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            Map<String, Boolean> result = new LinkedHashMap<>();
            result.put("policy_ready", toBool(row.get("policy_ready")));
            result.put("score_segment_ready", toBool(row.get("score_segment_ready")));
            result.put("admission_plan_ready", toBool(row.get("admission_plan_ready")));
            result.put("major_requirement_ready", toBool(row.get("major_requirement_ready")));
            result.put("major_meta_ready", toBool(row.get("major_meta_ready")));
            result.put("ml_training_ready", toBool(row.get("ml_training_ready")));
            result.put("historical_training_ready", toBool(row.get("historical_training_ready")));
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void upsertReadiness(String provinceCode, int year, Map<String, Boolean> flags, Boolean keepMl, String remarks) {
        int ml = keepMl == null ? 0 : (keepMl ? 1 : 0);
        int updated = jdbcTemplate.update(
                "UPDATE data_year_readiness SET "
                        + "policy_ready=?, score_segment_ready=?, admission_plan_ready=?, "
                        + "major_requirement_ready=?, major_meta_ready=?, ml_training_ready=?, "
                        + "historical_training_ready=?, remarks=?, last_checked_at=NOW(), updated_at=NOW() "
                        + "WHERE province_code = ? AND year = ?",
                boolBit(flags.get("policy_ready")),
                boolBit(flags.get("score_segment_ready")),
                boolBit(flags.get("admission_plan_ready")),
                boolBit(flags.get("major_requirement_ready")),
                boolBit(flags.get("major_meta_ready")),
                ml,
                boolBit(flags.get("historical_training_ready")),
                remarks.length() > 500 ? remarks.substring(0, 500) : remarks,
                provinceCode, year);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO data_year_readiness "
                            + "(province_code, year, policy_ready, score_segment_ready, admission_plan_ready, "
                            + "major_requirement_ready, major_meta_ready, ml_training_ready, historical_training_ready, "
                            + "recommendation_phase, remarks, last_checked_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PRE_OFFICIAL_DATA', ?, NOW(), NOW(), NOW())",
                    provinceCode, year,
                    boolBit(flags.get("policy_ready")),
                    boolBit(flags.get("score_segment_ready")),
                    boolBit(flags.get("admission_plan_ready")),
                    boolBit(flags.get("major_requirement_ready")),
                    boolBit(flags.get("major_meta_ready")),
                    ml,
                    boolBit(flags.get("historical_training_ready")),
                    remarks.length() > 500 ? remarks.substring(0, 500) : remarks);
        }
    }

    private static int boolBit(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }

    public DataYearReadinessDto buildDataReadinessDto(String provinceCode, int year) {
        String normalizedProvince = normalizeProvinceCode(provinceCode);
        BatchSupportService.DataReadiness readiness = getOrDefaultReadiness(normalizedProvince, year);
        DataYearReadinessDto dto = new DataYearReadinessDto();
        dto.setProvinceCode(normalizedProvince);
        dto.setYear(year);
        dto.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        dto.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        dto.setTrainingYears(admissionYearService.resolveTrainingYears());
        dto.setDataSourceYears(resolveDataSourceYears(readiness, year));
        dto.setRecommendationPhase(readiness.getRecommendationPhase());
        dto.setOfficialDataReady(admissionYearService.isOfficialDataReady(readiness));
        dto.setModelRetrained(isModelRetrained(normalizedProvince, year));
        dto.setEstimateMode(admissionYearService.isPreOfficialDataPhase(readiness.getRecommendationPhase()));
        dto.setDataReadiness(readiness);
        dto.setPhaseDescription(phaseDescription(readiness.getRecommendationPhase()));
        dto.setNextActions(nextActions(readiness));
        return dto;
    }

    private BatchSupportService.DataReadiness defaultReadiness(String provinceCode, int year) {
        BatchSupportService.DataReadiness readiness = admissionYearService.defaultDataReadiness(year);
        readiness.setProvinceCode(provinceCode);
        readiness.setYear(year);
        readiness.setRecommendationPhase(admissionYearService.normalizeRecommendationPhase(readiness.getRecommendationPhase()));
        readiness.setHistoricalTrainingReady(!admissionYearService.resolveHistoryYears(year).isEmpty());
        return readiness;
    }

    private BatchSupportService.DataReadiness readinessFromRow(Map<String, Object> row, String provinceCode, int year) {
        BatchSupportService.DataReadiness readiness = defaultReadiness(provinceCode, year);
        readiness.setPolicyReady(toBool(row.get("policy_ready")));
        readiness.setScoreSegmentReady(toBool(row.get("score_segment_ready")));
        readiness.setAdmissionPlanReady(toBool(row.get("admission_plan_ready")));
        readiness.setMajorRequirementReady(toBool(row.get("major_requirement_ready")));
        readiness.setMajorMetaReady(toBool(row.get("major_meta_ready")));
        readiness.setMlTrainingReady(toBool(row.get("ml_training_ready")));
        readiness.setHistoricalTrainingReady(toBool(row.get("historical_training_ready")));
        Object phase = row.get("recommendation_phase");
        readiness.setRecommendationPhase(admissionYearService.normalizeRecommendationPhase(phase == null ? null : phase.toString()));
        Object batchId = row.get("latest_import_batch_id");
        readiness.setLatestImportBatchId(batchId == null ? "" : batchId.toString());
        Object checkedAt = row.get("last_checked_at");
        readiness.setLastCheckedAt(checkedAt == null ? "" : checkedAt.toString());
        Object remarks = row.get("remarks");
        readiness.setRemarks(remarks == null ? "" : remarks.toString());
        return readiness;
    }

    private List<Integer> resolveDataSourceYears(BatchSupportService.DataReadiness readiness, int year) {
        List<Integer> years = new ArrayList<>(admissionYearService.resolveTrainingYears());
        if (year == admissionYearService.getTargetYear()
                && admissionYearService.isModelRetrainedPhase(readiness.getRecommendationPhase())
                && readiness.isMlTrainingReady()
                && !years.contains(year)) {
            years.add(year);
        }
        return years;
    }

    private String phaseDescription(String phase) {
        return switch (admissionYearService.normalizeRecommendationPhase(phase)) {
            case AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL -> "2026 部分官方数据已导入或进入质检，仍禁止完整推荐。";
            case AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED -> "2026 关键官方数据已导入，模型重训完成前最多开放试推荐。";
            case AdmissionYearService.PHASE_MODEL_RETRAINED -> "2026 数据已进入训练并完成模型切换，可按批次质量门禁开放完整推荐。";
            default -> "2026 官方数据未发布或未完成导入，当前仅提供历史趋势和预估参考。";
        };
    }

    private List<String> nextActions(BatchSupportService.DataReadiness readiness) {
        List<String> actions = new ArrayList<>();
        if (!readiness.isScoreSegmentReady()) {
            actions.add("导入 2026 一分一段表");
        }
        if (!readiness.isAdmissionPlanReady()) {
            actions.add("导入 2026 招生计划");
        }
        if (!readiness.isPolicyReady()) {
            actions.add("确认 2026 批次政策规则");
        }
        if (!readiness.isMajorRequirementReady()) {
            actions.add("导入 2026 选科要求");
        }
        if (!readiness.isMajorMetaReady()) {
            actions.add("补齐 2026 专业备注和限制字段");
        }
        if (!readiness.isMlTrainingReady()) {
            actions.add("完成模型重训后再开放正式推荐");
        }
        if (actions.isEmpty()) {
            actions.add("保持 readiness、模型和 batch-support smoke 监控");
        }
        return actions;
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

    private boolean toBool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return value != null && ("1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString()));
    }

    private String normalizeProvinceCode(String provinceCode) {
        return provinceCode == null || provinceCode.isBlank() ? "GZ" : provinceCode.trim().toUpperCase();
    }

    @Data
    public static class DataYearReadinessDto {
        private String provinceCode;
        private int year;
        private int activeAdmissionYear;
        private int latestOfficialDataYear;
        private List<Integer> trainingYears = List.of();
        private List<Integer> dataSourceYears = List.of();
        private String recommendationPhase;
        private boolean officialDataReady;
        private boolean modelRetrained;
        private boolean estimateMode;
        private BatchSupportService.DataReadiness dataReadiness;
        private String phaseDescription;
        private List<String> nextActions = List.of();
    }

    @Data
    public static class RefreshResult {
        private final String provinceCode;
        private final int year;
        private final Map<String, Boolean> before;
        private final Map<String, Boolean> after;
        private final Map<String, Long> rowCounts;
        private final String remarks;
    }
}
