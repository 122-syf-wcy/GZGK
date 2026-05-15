package com.gzly.service;

import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DataYearReadinessService {

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
}
