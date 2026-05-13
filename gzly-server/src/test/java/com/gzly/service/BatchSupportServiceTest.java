package com.gzly.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BatchSupportServiceTest {

    @Test
    void batchSupport_shouldReturnAllGuizhouBatches() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        assertThat(response.getItems()).hasSizeGreaterThan(1);
        assertThat(codes(response)).contains(
                "NORMAL_UNDERGRADUATE",
                "NORMAL_SPECIALTY",
                "EARLY_A_B",
                "EARLY_C",
                "SPECIALTY_EARLY",
                "ART_UNDERGRADUATE_A",
                "ART_UNDERGRADUATE_B",
                "ART_SPECIALTY",
                "SPORTS_UNDERGRADUATE",
                "SPORTS_SPECIALTY",
                "NATIONAL_SPECIAL",
                "LOCAL_SPECIAL",
                "UNIVERSITY_SPECIAL",
                "ETHNIC_CLASS",
                "PREPARATORY",
                "ORIENTED",
                "FREE_MEDICAL",
                "TEACHER_EXCELLENCE");
    }

    @Test
    void normalUndergraduate_shouldExposeFullRecommendWhenPolicyAndDataReady() {
        BatchSupportService.BatchSupportItem item = item(readyService(), "NORMAL_UNDERGRADUATE");

        assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
        assertThat(item.getMaxVolunteerCount()).isEqualTo(96);
        assertThat(item.getPolicyStatus()).isEqualTo("confirmed");
        assertThat(item.getDataStatus().getStatus()).isEqualTo("READY");
    }

    @Test
    void normalSpecialty_shouldExistAndNeverBeMissing() {
        BatchSupportService.BatchSupportItem item = item(readyService(), "NORMAL_SPECIALTY");

        assertThat(item).isNotNull();
        assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
        assertThat(item.getDataStatus().getStatus()).isEqualTo("READY");
        assertThat(item.getPlanCount()).isEqualTo(10);
    }

    @Test
    void batchSupport_shouldReturnEngineName() {
        BatchSupportService.BatchSupportItem item = item(serviceWithNoDatabaseRows(), "NORMAL_UNDERGRADUATE");

        assertThat(item.getEngineName()).isEqualTo("OrdinaryParallelMajorEngine");
    }

    @Test
    void batchSupport_shouldReturnRecommendMode() {
        BatchSupportService.BatchSupportItem item = item(serviceWithNoDatabaseRows(), "EARLY_C");

        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR_60");
    }

    @Test
    void normalSpecialty_supportLevel_shouldMatchRecommend() {
        BatchSupportService.BatchSupportItem item = item(readyService(), "NORMAL_SPECIALTY");

        assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(item.getEngineName()).isEqualTo("OrdinaryParallelMajorEngine");
        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
    }

    @Test
    void earlyAB_shouldReturnSequentialCollegeOrQueryOnlyMode() {
        BatchSupportService.BatchSupportItem item = item(serviceWithNoDatabaseRows(), "EARLY_A_B");

        assertThat(item.getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(item.getRecommendMode()).isIn("SEQUENTIAL_COLLEGE", "QUERY_ONLY");
        assertThat(item.getWarnings()).isNotEmpty();
    }

    @Test
    void earlyC_shouldNotMapToSpecialtyEarly() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        BatchSupportService.BatchSupportItem earlyC = item(response, "EARLY_C");
        BatchSupportService.BatchSupportItem specialtyEarly = item(response, "SPECIALTY_EARLY");
        assertThat(earlyC.getBatchName()).contains("本科提前批C段");
        assertThat(specialtyEarly.getBatchName()).contains("高职专科提前批");
        assertThat(earlyC.getBatchCode()).isNotEqualTo(specialtyEarly.getBatchCode());
    }

    @Test
    void artAndSportsUndergraduate_shouldBeQueryOnlyOrUnsupported() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        assertThat(item(response, "ART_UNDERGRADUATE_B").getSupportLevel()).isIn("QUERY_ONLY", "UNSUPPORTED");
        assertThat(item(response, "SPORTS_UNDERGRADUATE").getSupportLevel()).isIn("QUERY_ONLY", "UNSUPPORTED");
    }

    @Test
    void specialPrograms_shouldAppearInMatrix() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        assertThat(codes(response)).contains(
                "NATIONAL_SPECIAL",
                "LOCAL_SPECIAL",
                "UNIVERSITY_SPECIAL",
                "ETHNIC_CLASS",
                "PREPARATORY",
                "ORIENTED",
                "FREE_MEDICAL",
                "TEACHER_EXCELLENCE");
    }

    @Test
    void unsupportedOrQueryOnlyBatches_shouldHaveWarningsAndReason() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        response.getItems().stream()
                .filter(item -> !"FULL_RECOMMEND".equals(item.getSupportLevel()) && !"TRIAL_RECOMMEND".equals(item.getSupportLevel()))
                .forEach(item -> {
                    assertThat(item.getWarnings()).as(item.getBatchCode()).isNotEmpty();
                    assertThat(item.getSupportReason()).as(item.getBatchCode()).isNotBlank();
                });
    }

    @Test
    void batchSupport_shouldNotOnlyReturnNormalUndergraduate() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2025);

        assertThat(response.getItems()).hasSizeGreaterThan(10);
        assertThat(codes(response)).isNotEqualTo(List.of("NORMAL_UNDERGRADUATE"));
    }

    @Test
    void publicBatchSupport_shouldDefaultToActiveAdmissionYearAndExposeHistoryYears() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", null, true);

        assertThat(response.getYear()).isEqualTo(2026);
        assertThat(response.getActiveAdmissionYear()).isEqualTo(2026);
        assertThat(response.getLatestOfficialDataYear()).isEqualTo(2025);
        assertThat(response.getHistoryYears()).containsExactly(2025, 2024);
        assertThat(response.getTrainingYears()).containsExactly(2024, 2025);
        assertThat(response.getDataSourceYears()).containsExactly(2024, 2025);
        assertThat(response.getRecommendationPhase()).isEqualTo("PRE_OFFICIAL_DATA");
        assertThat(response.isEstimateMode()).isTrue();
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(response.isPublicYearLocked()).isTrue();
    }

    @Test
    void publicActiveYear_shouldExposeEstimateRecommendForNormalBatchesBeforeOfficialDataReady() {
        BatchSupportService.BatchSupportItem item = item(readyService().supportMatrix("GZ", 2026, true), "NORMAL_UNDERGRADUATE");

        assertThat(item.getSupportLevel()).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(item.getDataStatus().getStatus()).isEqualTo("PRE_OFFICIAL_DATA");
        assertThat(item.getSupportReason()).isEqualTo(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
        assertThat(item.getWarnings()).contains(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
    }

    @Test
    void PRE_OFFICIAL_DATA_shouldNotExposeFullOrTrialAndShouldOnlyEstimateForNormalBatches() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(response.getSummary()).containsEntry("TRIAL_RECOMMEND", 0L);
        assertThat(response.getSummary().get("ESTIMATE_RECOMMEND")).isEqualTo(2L);
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(response.isEstimateMode()).isTrue();
        response.getItems().forEach(item -> {
            String code = item.getBatchCode();
            if ("NORMAL_UNDERGRADUATE".equals(code) || "NORMAL_SPECIALTY".equals(code)) {
                assertThat(item.getSupportLevel()).as(code).isEqualTo("ESTIMATE_RECOMMEND");
                assertThat(item.getWarnings()).as(code).contains(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
            } else {
                assertThat(item.getSupportLevel()).as(code).isEqualTo("QUERY_ONLY");
            }
        });
    }

    @Test
    void publicActiveYear_shouldKeepQueryOnlyWhenOfficialDataPartial() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL, false)
        ).supportMatrix("GZ", 2026, true);
        BatchSupportService.BatchSupportItem item = item(response, "NORMAL_UNDERGRADUATE");

        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(response.isEstimateMode()).isFalse();
        assertThat(item.getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(item.getDataStatus().getStatus()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
        assertThat(item.getWarnings()).contains(AdmissionYearService.OFFICIAL_DATA_PARTIAL_WARNING);
    }

    @Test
    void OFFICIAL_DATA_PARTIAL_shouldNotFullRecommend() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL, false)
        ).supportMatrix("GZ", 2026, true);

        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(item(response, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(item(response, "NORMAL_SPECIALTY").getSupportLevel()).isEqualTo("QUERY_ONLY");
    }

    @Test
    void publicActiveYear_shouldOnlyExposeTrialRecommendAfterOfficialDataImportedBeforeRetrain() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false)
        ).supportMatrix("GZ", 2026, true);
        BatchSupportService.BatchSupportItem undergraduate = item(response, "NORMAL_UNDERGRADUATE");
        BatchSupportService.BatchSupportItem specialty = item(response, "NORMAL_SPECIALTY");

        assertThat(response.isOfficialDataReady()).isTrue();
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED);
        assertThat(undergraduate.getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
        assertThat(specialty.getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
        assertThat(undergraduate.getSupportReason()).contains("试推荐");
        assertThat(undergraduate.getWarnings()).contains(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
    }

    @Test
    void OFFICIAL_DATA_IMPORTED_shouldAtMostTrialRecommend() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false)
        ).supportMatrix("GZ", 2026, true);

        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(item(response, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
        assertThat(item(response, "NORMAL_SPECIALTY").getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
    }

    @Test
    void officialDataImported_withoutMlTraining_shouldNotFullRecommend() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false)
        ).supportMatrix("GZ", 2026, true);

        assertThat(response.getDataReadiness().isMlTrainingReady()).isFalse();
        assertThat(response.getItems()).noneSatisfy(item -> assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND"));
    }

    @Test
    void publicActiveYear_shouldExposeFullRecommendAfterModelRetrained() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_MODEL_RETRAINED, true)
        ).supportMatrix("GZ", 2026, true);
        BatchSupportService.BatchSupportItem item = item(response, "NORMAL_UNDERGRADUATE");

        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_MODEL_RETRAINED);
        assertThat(response.isOfficialDataReady()).isTrue();
        assertThat(response.getDataSourceYears()).contains(2026);
        assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(item.getWarnings()).doesNotContain(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
    }

    @Test
    void MODEL_RETRAINED_withMlReady_shouldAllowFullRecommendWhenBatchReady() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_MODEL_RETRAINED, true)
        ).supportMatrix("GZ", 2026, true);

        assertThat(response.isOfficialDataReady()).isTrue();
        assertThat(response.getDataReadiness().isMlTrainingReady()).isTrue();
        assertThat(response.getDataReadiness().isMajorMetaReady()).isTrue();
        assertThat(item(response, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(item(response, "NORMAL_SPECIALTY").getSupportLevel()).isEqualTo("FULL_RECOMMEND");
    }

    @Test
    void publicBatchSupport_shouldExposeReadiness() {
        BatchSupportService.BatchSupportResponse response = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL, false)
        ).supportMatrix("GZ", 2026, true);

        assertThat(response.getActiveAdmissionYear()).isEqualTo(2026);
        assertThat(response.getLatestOfficialDataYear()).isEqualTo(2025);
        assertThat(response.getTrainingYears()).containsExactly(2024, 2025);
        assertThat(response.getDataSourceYears()).containsExactly(2024, 2025);
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
        assertThat(response.getDataReadiness().getProvinceCode()).isEqualTo("GZ");
        assertThat(response.getDataReadiness().getYear()).isEqualTo(2026);
        assertThat(response.getDataReadiness().isPolicyReady()).isTrue();
        assertThat(response.isPublicYearLocked()).isTrue();
    }

    @Test
    void adminBacktestBatchSupport_shouldAllowHistoricalYear() {
        BatchSupportService.BatchSupportResponse response = serviceWithNoDatabaseRows().supportMatrix("GZ", 2024, false);

        assertThat(response.getYear()).isEqualTo(2024);
        assertThat(response.getActiveAdmissionYear()).isEqualTo(2026);
        assertThat(response.getRecommendationPhase()).isEqualTo("MODEL_RETRAINED");
        assertThat(response.isEstimateMode()).isFalse();
        assertThat(response.isPublicYearLocked()).isFalse();
    }

    // === 2026 预估推荐策略：PRE_OFFICIAL_DATA 普通本/专科开放 ESTIMATE_RECOMMEND，非普通批保持 QUERY_ONLY ===

    @Test
    void PRE_OFFICIAL_DATA_normalUndergraduate_shouldAllowEstimateRecommend() {
        BatchSupportService.BatchSupportItem item = item(readyService().supportMatrix("GZ", 2026, true), "NORMAL_UNDERGRADUATE");

        assertThat(item.getSupportLevel()).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
    }

    @Test
    void PRE_OFFICIAL_DATA_normalSpecialty_shouldAllowEstimateRecommend() {
        BatchSupportService.BatchSupportItem item = item(readyService().supportMatrix("GZ", 2026, true), "NORMAL_SPECIALTY");

        assertThat(item.getSupportLevel()).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(item.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
    }

    @Test
    void PRE_OFFICIAL_DATA_earlyBatch_shouldStayQueryOnly() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        assertThat(item(response, "EARLY_A_B").getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(item(response, "EARLY_C").getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(item(response, "SPECIALTY_EARLY").getSupportLevel()).isEqualTo("QUERY_ONLY");
    }

    @Test
    void PRE_OFFICIAL_DATA_artSports_shouldStayQueryOnly() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        for (String code : List.of("ART_UNDERGRADUATE_A", "ART_UNDERGRADUATE_B", "ART_SPECIALTY",
                "SPORTS_UNDERGRADUATE", "SPORTS_SPECIALTY")) {
            assertThat(item(response, code).getSupportLevel()).as(code).isEqualTo("QUERY_ONLY");
        }
    }

    @Test
    void PRE_OFFICIAL_DATA_specialPlans_shouldStayQueryOnly() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        for (String code : List.of("NATIONAL_SPECIAL", "LOCAL_SPECIAL", "UNIVERSITY_SPECIAL",
                "ETHNIC_CLASS", "PREPARATORY", "ORIENTED", "FREE_MEDICAL", "TEACHER_EXCELLENCE")) {
            assertThat(item(response, code).getSupportLevel()).as(code).isEqualTo("QUERY_ONLY");
        }
    }

    @Test
    void estimateRecommend_shouldSetEstimateModeTrue() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        assertThat(response.isEstimateMode()).isTrue();
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(response.isOfficialDataReady()).isFalse();
    }

    @Test
    void estimateRecommend_shouldUseDataSourceYears2024And2025() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        assertThat(response.getDataSourceYears()).containsExactly(2024, 2025);
        assertThat(response.getDataSourceYears()).doesNotContain(2026);
    }

    @Test
    void estimateRecommend_shouldNotMarkFullRecommend() {
        BatchSupportService.BatchSupportResponse response = readyService().supportMatrix("GZ", 2026, true);

        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(response.getItems())
                .noneSatisfy(item -> assertThat(item.getSupportLevel()).isEqualTo("FULL_RECOMMEND"));
    }

    @Test
    void estimateRecommend_shouldAddOfficialDataWarning() {
        BatchSupportService.BatchSupportItem item = item(readyService().supportMatrix("GZ", 2026, true), "NORMAL_UNDERGRADUATE");

        assertThat(item.getSupportLevel()).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(item.getWarnings()).contains(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
        assertThat(item.getSupportReason()).isEqualTo(AdmissionYearService.PRE_OFFICIAL_DATA_ESTIMATE_WARNING);
    }

    @Test
    void fullRecommend_shouldOnlyAllowedAfterOfficialDataReadyAndModelRetrained() {
        BatchSupportService.BatchSupportResponse preOfficial = readyService().supportMatrix("GZ", 2026, true);
        BatchSupportService.BatchSupportResponse imported = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false)
        ).supportMatrix("GZ", 2026, true);
        BatchSupportService.BatchSupportResponse retrained = serviceWithReadiness(
                readinessRow(AdmissionYearService.PHASE_MODEL_RETRAINED, true)
        ).supportMatrix("GZ", 2026, true);

        assertThat(item(preOfficial, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("ESTIMATE_RECOMMEND");
        assertThat(item(imported, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
        assertThat(item(retrained, "NORMAL_UNDERGRADUATE").getSupportLevel()).isEqualTo("FULL_RECOMMEND");

        assertThat(preOfficial.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(imported.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat((long) retrained.getSummary().get("FULL_RECOMMEND")).isGreaterThanOrEqualTo(2L);
    }

    private BatchSupportService serviceWithNoDatabaseRows() {
        return new BatchSupportService(new FakeJdbcTemplate(false), admissionYearService());
    }

    private BatchSupportService readyService() {
        return new BatchSupportService(new FakeJdbcTemplate(true), admissionYearService());
    }

    private BatchSupportService serviceWithReadiness(Map<String, Object> readinessRow) {
        return new BatchSupportService(new FakeJdbcTemplate(true, readinessRow), admissionYearService());
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
        row.put("latest_import_batch_id", "gz_2026_admission_plan_v1_20260625");
        row.put("last_checked_at", "2026-06-25 12:00:00");
        row.put("remarks", "test readiness");
        return row;
    }

    private AdmissionYearService admissionYearService() {
        AdmissionYearService service = new AdmissionYearService();
        service.setActiveAdmissionYear(2026);
        service.setHistoryYears("2025,2024");
        service.setTrainingYears("2024,2025");
        return service;
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {

        private final boolean ready;
        private final Map<String, Object> readinessRow;

        private FakeJdbcTemplate(boolean ready) {
            this(ready, null);
        }

        private FakeJdbcTemplate(boolean ready, Map<String, Object> readinessRow) {
            this.ready = ready;
            this.readinessRow = readinessRow;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (Long.class.equals(requiredType)) {
                if (!ready) {
                    return requiredType.cast(0L);
                }
                if (sql.contains("information_schema.columns")) {
                    String table = String.valueOf(args[0]);
                    String column = String.valueOf(args[1]);
                    if (("data_admission_plan_gz".equals(table) || "admission_plan".equals(table)) && "batch_code".equals(column)) {
                        return requiredType.cast(1L);
                    }
                    if (("data_score_line_gz".equals(table) || "data_major_score_gz".equals(table)) && "batch".equals(column)) {
                        return requiredType.cast(1L);
                    }
                    return requiredType.cast(0L);
                }
                return requiredType.cast(1L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!ready) {
                return List.of();
            }
            if (sql.contains("FROM data_year_readiness")) {
                return readinessRow == null ? List.of() : List.of(readinessRow);
            }
            if (sql.startsWith("SELECT policy_status")) {
                return List.of(Map.of(
                        "policy_status", "confirmed",
                        "max_volunteer_count", 96,
                        "major_per_school_count", 0,
                        "has_adjustment", 0));
            }
            if (sql.contains("data_score_line_gz")) {
                return List.of(Map.of("batch", "普通本科批", "cnt", 12L), Map.of("batch", "普通类高职专科批", "cnt", 8L));
            }
            if (sql.contains("data_major_score_gz")) {
                return List.of(Map.of("batch", "普通本科批", "cnt", 20L), Map.of("batch", "普通类高职专科批", "cnt", 6L));
            }
            if (sql.contains("data_admission_plan_gz")) {
                return List.of(Map.of("data_batch", "普通本科批", "cnt", 30L), Map.of("data_batch", "NORMAL_SPECIALIST", "cnt", 10L));
            }
            if (sql.contains("FROM admission_plan")) {
                return List.of();
            }
            return List.of();
        }
    }

    private BatchSupportService.BatchSupportItem item(BatchSupportService service, String batchCode) {
        return item(service.supportMatrix("GZ", 2025), batchCode);
    }

    private BatchSupportService.BatchSupportItem item(BatchSupportService.BatchSupportResponse response, String batchCode) {
        return response.getItems().stream()
                .filter(item -> batchCode.equals(item.getBatchCode()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> codes(BatchSupportService.BatchSupportResponse response) {
        return response.getItems().stream().map(BatchSupportService.BatchSupportItem::getBatchCode).toList();
    }
}
