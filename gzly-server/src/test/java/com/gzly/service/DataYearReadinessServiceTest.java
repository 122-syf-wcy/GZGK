package com.gzly.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataYearReadinessServiceTest {

    @Test
    void missingReadinessRow_shouldFallbackPreOfficialData() {
        DataYearReadinessService service = new DataYearReadinessService(new FakeJdbcTemplate(false, null), admissionYearService());

        BatchSupportService.DataReadiness readiness = service.getOrDefaultReadiness("GZ", 2026);
        DataYearReadinessService.DataYearReadinessDto dto = service.buildDataReadinessDto("GZ", 2026);

        assertThat(readiness.getProvinceCode()).isEqualTo("GZ");
        assertThat(readiness.getYear()).isEqualTo(2026);
        assertThat(readiness.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(readiness.isHistoricalTrainingReady()).isTrue();
        assertThat(service.isOfficialDataReady("GZ", 2026)).isFalse();
        assertThat(service.isModelRetrained("GZ", 2026)).isFalse();
        assertThat(dto.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(dto.isEstimateMode()).isTrue();
        assertThat(dto.getNextActions()).contains("导入 2026 一分一段表", "导入 2026 招生计划", "完成模型重训后再开放正式推荐");
    }

    @Test
    void getReadiness_shouldMapDatabaseRow() {
        DataYearReadinessService service = new DataYearReadinessService(new FakeJdbcTemplate(true, readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false)), admissionYearService());

        BatchSupportService.DataReadiness readiness = service.getReadiness("gz", 2026).orElseThrow();

        assertThat(readiness.getProvinceCode()).isEqualTo("GZ");
        assertThat(readiness.getYear()).isEqualTo(2026);
        assertThat(readiness.isPolicyReady()).isTrue();
        assertThat(readiness.isScoreSegmentReady()).isTrue();
        assertThat(readiness.isAdmissionPlanReady()).isTrue();
        assertThat(readiness.isMajorRequirementReady()).isTrue();
        assertThat(readiness.isMajorMetaReady()).isTrue();
        assertThat(readiness.isMlTrainingReady()).isFalse();
        assertThat(readiness.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED);
        assertThat(readiness.getLatestImportBatchId()).isEqualTo("gz_2026_all_v1_20260625");
        assertThat(service.isOfficialDataReady("GZ", 2026)).isTrue();
        assertThat(service.isModelRetrained("GZ", 2026)).isFalse();
    }

    @Test
    void modelRetrained_shouldRequireMlReadyAndMajorMetaReady() {
        Map<String, Object> row = readinessRow(AdmissionYearService.PHASE_MODEL_RETRAINED, true);
        DataYearReadinessService service = new DataYearReadinessService(new FakeJdbcTemplate(true, row), admissionYearService());

        assertThat(service.isModelRetrained("GZ", 2026)).isTrue();

        row.put("major_meta_ready", 0);
        DataYearReadinessService notReady = new DataYearReadinessService(new FakeJdbcTemplate(true, row), admissionYearService());
        assertThat(notReady.isModelRetrained("GZ", 2026)).isFalse();
    }

    private AdmissionYearService admissionYearService() {
        AdmissionYearService service = new AdmissionYearService();
        service.setActiveAdmissionYear(2026);
        service.setLatestOfficialDataYear(2025);
        service.setTargetYear(2026);
        service.setHistoryYears("2025,2024");
        service.setTrainingYears("2024,2025");
        return service;
    }

    private Map<String, Object> readinessRow(String phase, boolean mlReady) {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_ready", 1);
        row.put("score_segment_ready", 1);
        row.put("admission_plan_ready", 1);
        row.put("major_requirement_ready", 1);
        row.put("major_meta_ready", 1);
        row.put("ml_training_ready", mlReady ? 1 : 0);
        row.put("historical_training_ready", 1);
        row.put("recommendation_phase", phase);
        row.put("latest_import_batch_id", "gz_2026_all_v1_20260625");
        row.put("last_checked_at", "2026-06-25 12:00:00");
        row.put("remarks", "ready");
        return row;
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        private final boolean tableExists;
        private final Map<String, Object> readinessRow;

        private FakeJdbcTemplate(boolean tableExists, Map<String, Object> readinessRow) {
            this.tableExists = tableExists;
            this.readinessRow = readinessRow;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (Long.class.equals(requiredType)) {
                return requiredType.cast(tableExists ? 1L : 0L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!tableExists || readinessRow == null) {
                return List.of();
            }
            if (sql.contains("FROM data_year_readiness")) {
                return List.of(readinessRow);
            }
            return List.of();
        }
    }
}
