package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HubeiBatchSupportServiceTest {

    @Test
    void supportMatrix_shouldExposeHbPrefixedBatchesWithoutScLeakage() {
        HubeiBatchSupportService service = new HubeiBatchSupportService(
                null, admissionYearService(), null);

        BatchSupportService.BatchSupportResponse response =
                service.supportMatrix("HB", 2026, true);

        assertThat(response.getProvinceCode()).isEqualTo("HB");
        assertThat(response.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        assertThat(response.isOfficialDataReady()).isFalse();
        assertThat(response.isEstimateMode()).isTrue();
        assertThat(response.getItems()).extracting(BatchSupportService.BatchSupportItem::getBatchCode)
                .containsExactly("HB_BENKE", "HB_ZHUANKE", "HB_EARLY", "HB_SPECIAL", "HB_ART", "HB_SPORTS")
                .allSatisfy(code -> assertThat(code).doesNotStartWith("SC_"));
        assertThat(response.getSummary()).containsEntry("FULL_RECOMMEND", 0L);
        assertThat(response.getItems().get(0).getBatchName()).isEqualTo("本科普通批");
    }

    @Test
    void registry_shouldNormalizeDefaultBatchForHubeiOnly() {
        assertThat(HubeiBatchRuleRegistry.normalizeBatchCode(null)).isEqualTo("HB_BENKE");
        assertThat(HubeiBatchRuleRegistry.normalizeBatchCode("本科普通批")).isEqualTo("HB_BENKE");
        assertThat(HubeiBatchRuleRegistry.normalizeBatchCode("体育本科批")).isEqualTo("HB_SPORTS");
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
