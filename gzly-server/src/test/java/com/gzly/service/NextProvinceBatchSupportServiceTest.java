package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
class NextProvinceBatchSupportServiceTest {

    @Test
    void supportMatrix_shouldExposeHistoricalEstimateAndQueryOnlyBatchesForEveryNextProvince() {
        NextProvinceBatchSupportService service = new NextProvinceBatchSupportService(
                admissionYearService(), null);

        for (NextProvincePolicyRegistry.Profile profile : NextProvincePolicyRegistry.allProfiles()) {
            BatchSupportService.BatchSupportResponse response =
                    service.supportMatrix(profile.provinceCode(), 2026, true);

            assertThat(response.getProvinceCode()).isEqualTo(profile.provinceCode());
            assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
            assertThat(response.isOfficialDataReady()).isFalse();
            assertThat(response.isEstimateMode()).isTrue();
            assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
            assertThat(response.getSummary()).containsEntry("TRIAL_RECOMMEND", 2L);
            assertThat(response.getSummary()).containsEntry("QUERY_ONLY", 4L);
            assertThat(response.getItems()).hasSize(6);

            BatchSupportService.BatchSupportItem item = response.getItems().get(0);
            assertThat(item.getBatchCode()).isEqualTo(profile.defaultBatchCode());
            assertThat(item.getEngineName()).isEqualTo("QueryOnlyRecommendEngine");
            assertThat(item.getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
            assertThat(item.getRecommendMode()).isEqualTo("QUERY_ONLY");
            assertThat(item.getMissingData()).contains(profile.provinceCode() + "_2026_plan");
            assertThat(item.getSupportReason()).contains("历史估算");
            assertThat(item.getDataStatus().getDetail()).contains("历史估算能力");
            assertThat(response.getItems()).anySatisfy(queryOnly -> {
                assertThat(queryOnly.getSupportLevel()).isEqualTo("QUERY_ONLY");
                assertThat(queryOnly.getSupportReason()).isNotBlank();
            });
        }
    }

    @Test
    void legacyBatchSupportService_shouldNotReturnGuizhouBatchesForNextProvince() {
        BatchSupportService service = new BatchSupportService(null, admissionYearService());

        BatchSupportService.BatchSupportResponse response = service.supportMatrix("GX", 2026, true);

        assertThat(response.getProvinceCode()).isEqualTo("GX");
        assertThat(response.getItems()).extracting(BatchSupportService.BatchSupportItem::getBatchCode)
                .containsExactly("GX_BENKE", "GX_ZHUANKE", "GX_EARLY", "GX_SPECIAL", "GX_ART", "GX_SPORTS");
        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(response.getSummary()).containsEntry("TRIAL_RECOMMEND", 2L);
    }

    private AdmissionYearService admissionYearService() {
        AdmissionYearService service = new AdmissionYearService();
        service.setActiveAdmissionYear(2026);
        service.setLatestOfficialDataYear(2025);
        service.setHistoryYears("2025,2024");
        service.setTrainingYears("2024,2025");
        service.setTargetYear(2026);
        service.setFutureImportYear(2026);
        service.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        return service;
    }
}
