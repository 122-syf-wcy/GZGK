package com.gzly.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionYearServiceTest {

    @Test
    void recommendationPhase_shouldKeepOfficialDataPartial() {
        AdmissionYearService service = new AdmissionYearService();
        service.setRecommendationPhase(" official_data_partial ");

        assertThat(service.getRecommendationPhase()).isEqualTo(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
        assertThat(service.isOfficialDataPartialPhase(service.getRecommendationPhase())).isTrue();
        assertThat(service.isPreOfficialDataPhase(service.getRecommendationPhase())).isFalse();
    }

    @Test
    void officialDataReady_shouldRequireMajorMetaAndImportedOrRetrainedPhase() {
        AdmissionYearService service = new AdmissionYearService();
        BatchSupportService.DataReadiness readiness = readyReadiness();

        readiness.setRecommendationPhase(AdmissionYearService.PHASE_OFFICIAL_DATA_PARTIAL);
        assertThat(service.isOfficialDataReady(readiness)).isFalse();

        readiness.setRecommendationPhase(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED);
        readiness.setMajorMetaReady(false);
        assertThat(service.isOfficialDataReady(readiness)).isFalse();

        readiness.setMajorMetaReady(true);
        assertThat(service.isOfficialDataReady(readiness)).isTrue();

        readiness.setRecommendationPhase(AdmissionYearService.PHASE_MODEL_RETRAINED);
        assertThat(service.isOfficialDataReady(readiness)).isTrue();
    }

    private BatchSupportService.DataReadiness readyReadiness() {
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        readiness.setPolicyReady(true);
        readiness.setScoreSegmentReady(true);
        readiness.setAdmissionPlanReady(true);
        readiness.setMajorRequirementReady(true);
        readiness.setMajorMetaReady(true);
        readiness.setHistoricalTrainingReady(true);
        return readiness;
    }
}
