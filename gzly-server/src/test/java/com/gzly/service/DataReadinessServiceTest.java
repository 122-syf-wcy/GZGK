package com.gzly.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataReadinessServiceTest {

    @Test
    void shouldReadPersistedReadinessRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("HB"), eq(2026))).thenReturn(List.of(Map.ofEntries(
                Map.entry("province_code", "HB"),
                Map.entry("year", 2026),
                Map.entry("policy_ready", 1),
                Map.entry("score_segment_ready", 1),
                Map.entry("admission_plan_ready", 1),
                Map.entry("major_requirement_ready", 1),
                Map.entry("major_meta_ready", 1),
                Map.entry("ml_training_ready", 1),
                Map.entry("historical_training_ready", 1),
                Map.entry("recommendation_phase", DataReadinessService.FULL_RECOMMEND_READY),
                Map.entry("latest_import_batch_id", "batch-1"),
                Map.entry("last_checked_at", "2026-05-23T04:30:00"),
                Map.entry("remarks", "ok")
        )));

        DataReadinessService service = new DataReadinessService(jdbcTemplate);

        DataReadinessService.Readiness readiness = service.get("hb", 2026);

        assertThat(readiness.provinceCode).isEqualTo("HB");
        assertThat(readiness.recommendationPhase).isEqualTo(DataReadinessService.FULL_RECOMMEND_READY);
        assertThat(readiness.latestImportBatchId).isEqualTo("batch-1");
        assertThat(service.isFullRecommendReady("hb", 2026)).isTrue();

        ArgumentCaptor<String> sqlCaptor = forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).queryForList(sqlCaptor.capture(), eq("HB"), eq(2026));
        String sql = sqlCaptor.getAllValues().get(0);
        assertThat(sql).contains("COALESCE(latest_import_batch_id,'')");
        assertThat(sql).contains("DATE_FORMAT(COALESCE(last_checked_at,NOW()), '%Y-%m-%dT%H:%i:%s')");
        assertThat(sql).contains("COALESCE(remarks,'')");
    }

    @Test
    void fullRecommendRequiresExplicitFullReadyPhase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("SC"), eq(2026))).thenReturn(List.of(Map.ofEntries(
                Map.entry("province_code", "SC"),
                Map.entry("year", 2026),
                Map.entry("policy_ready", 1),
                Map.entry("score_segment_ready", 1),
                Map.entry("admission_plan_ready", 1),
                Map.entry("major_requirement_ready", 1),
                Map.entry("major_meta_ready", 1),
                Map.entry("ml_training_ready", 1),
                Map.entry("historical_training_ready", 1),
                Map.entry("recommendation_phase", DataReadinessService.OFFICIAL_DATA_IMPORTED),
                Map.entry("latest_import_batch_id", "batch-1"),
                Map.entry("last_checked_at", "2026-05-23T04:30:00"),
                Map.entry("remarks", "imported")
        )));

        DataReadinessService service = new DataReadinessService(jdbcTemplate);

        assertThat(service.isFullRecommendReady("sc", 2026)).isFalse();
        assertThat(service.phaseGates()).extracting(g -> g.get("phase"))
                .contains(DataReadinessService.PRE_OFFICIAL_DATA, DataReadinessService.FULL_RECOMMEND_READY);
    }
}
