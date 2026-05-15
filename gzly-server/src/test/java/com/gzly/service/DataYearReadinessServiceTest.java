package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void refreshReadinessFlags_shouldRejectNonGzProvince() {
        DataYearReadinessService service = new DataYearReadinessService(new FakeJdbcTemplate(true, null), admissionYearService());

        assertThatThrownBy(() -> service.refreshReadinessFlags("SC", 2025))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只支持 GZ 板块");
    }

    @Test
    void refreshReadinessFlags_shouldThresholdByRowCount() {
        // 模拟 GZ 2025 各表行数 全过 阈值 -> readiness flags 全 1
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(true, null);
        jdbc.countByPattern.put("policy_rule_config", 6L);
        jdbc.countByPattern.put("data_score_rank_gz", 1206L);
        jdbc.countByPattern.put("data_admission_plan_gz", 33331L);
        jdbc.countByPattern.put("data_major_requirement_gz", 34240L);
        jdbc.countByPattern.put("data_major_meta_gz", 36117L);
        jdbc.countByPattern.put("data_major_score_gz", 100000L);
        DataYearReadinessService service = new DataYearReadinessService(jdbc, admissionYearService());

        DataYearReadinessService.RefreshResult result = service.refreshReadinessFlags("gz", 2025);

        assertThat(result.getProvinceCode()).isEqualTo("GZ");
        assertThat(result.getYear()).isEqualTo(2025);
        assertThat(result.getAfter()).containsEntry("policy_ready", true)
                .containsEntry("score_segment_ready", true)
                .containsEntry("admission_plan_ready", true)
                .containsEntry("major_requirement_ready", true)
                .containsEntry("major_meta_ready", true)
                .containsEntry("historical_training_ready", true);
        assertThat(jdbc.updateCalls).isNotEmpty();
    }

    @Test
    void refreshReadinessFlags_shouldKeepFalseWhenBelowThreshold() {
        // GZ 2026 阶段 - 各表行数全 0 -> flags 全 0
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(true, null);
        DataYearReadinessService service = new DataYearReadinessService(jdbc, admissionYearService());

        DataYearReadinessService.RefreshResult result = service.refreshReadinessFlags("GZ", 2026);

        assertThat(result.getAfter()).containsEntry("policy_ready", false)
                .containsEntry("score_segment_ready", false)
                .containsEntry("admission_plan_ready", false)
                .containsEntry("major_requirement_ready", false)
                .containsEntry("major_meta_ready", false);
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
        final Map<String, Long> countByPattern = new HashMap<>();
        final java.util.List<String> updateCalls = new java.util.ArrayList<>();

        private FakeJdbcTemplate(boolean tableExists, Map<String, Object> readinessRow) {
            this.tableExists = tableExists;
            this.readinessRow = readinessRow;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (Long.class.equals(requiredType)) {
                // table_exists 检查
                if (sql.contains("information_schema.tables")) {
                    return requiredType.cast(tableExists ? 1L : 0L);
                }
                // 行数计数 - 按表名匹配
                for (Map.Entry<String, Long> e : countByPattern.entrySet()) {
                    if (sql.contains(e.getKey())) {
                        return requiredType.cast(e.getValue());
                    }
                }
                return requiredType.cast(0L);
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

        @Override
        public int update(String sql, Object... args) {
            updateCalls.add(sql);
            // 模拟 UPDATE 成功
            return sql.toUpperCase().startsWith("UPDATE") ? 1 : 0;
        }
    }
}
