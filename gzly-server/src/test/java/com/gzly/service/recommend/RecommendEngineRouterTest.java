package com.gzly.service.recommend;

import com.gzly.service.BatchSupportService;
import com.gzly.service.AdmissionYearService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommendEngineRouterTest {

    private RecommendEngineRouter router;

    @BeforeEach
    void setUp() {
        BatchSupportService batchSupportService = mock(BatchSupportService.class);
        QueryOnlyRecommendEngine queryOnlyRecommendEngine = new QueryOnlyRecommendEngine(new com.gzly.service.BatchQueryOnlyRecommendService());
        router = new RecommendEngineRouter(batchSupportService, queryOnlyRecommendEngine, new AdmissionYearService(), List.of(
                queryOnlyRecommendEngine,
                new OrdinaryParallelMajorEngine(),
                new SequentialCollegeEngine(),
                new EarlyCParallelMajorEngine(),
                new ArtCompositeRecommendEngine(),
                new SportsCompositeRecommendEngine(),
                new SpecialPlanEligibilityEngine()
        ));
    }

    @Test
    void normalUndergraduate_shouldRouteToOrdinaryParallelMajorEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "NORMAL_UNDERGRADUATE");

        assertThat(decision.getEngineName()).isEqualTo(OrdinaryParallelMajorEngine.NAME);
        assertThat(decision.isQueryOnly()).isFalse();
        assertThat(decision.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
    }

    @Test
    void normalSpecialty_shouldRouteToOrdinaryParallelMajorEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "NORMAL_SPECIALTY");

        assertThat(decision.getEngineName()).isEqualTo(OrdinaryParallelMajorEngine.NAME);
        assertThat(decision.isQueryOnly()).isFalse();
        assertThat(decision.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
    }

    @Test
    void earlyAB_shouldRouteToSequentialCollegeEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "EARLY_A_B");

        assertThat(decision.getEngineName()).isEqualTo(SequentialCollegeEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("SEQUENTIAL_COLLEGE");
    }

    @Test
    void specialtyEarly_shouldRouteToSequentialCollegeEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "SPECIALTY_EARLY");

        assertThat(decision.getEngineName()).isEqualTo(SequentialCollegeEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("SEQUENTIAL_COLLEGE");
    }

    @Test
    void earlyC_shouldRouteToEarlyCParallelMajorEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "EARLY_C");

        assertThat(decision.getEngineName()).isEqualTo(EarlyCParallelMajorEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("PARALLEL_MAJOR_60");
    }

    @Test
    void art_shouldRouteToArtCompositeRecommendEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "艺术类", "ART_UNDERGRADUATE_B");

        assertThat(decision.getEngineName()).isEqualTo(ArtCompositeRecommendEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("ART_COMPOSITE");
    }

    @Test
    void sports_shouldRouteToSportsCompositeRecommendEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "体育类", "SPORTS_UNDERGRADUATE");

        assertThat(decision.getEngineName()).isEqualTo(SportsCompositeRecommendEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("SPORTS_COMPOSITE");
    }

    @Test
    void specialPlan_shouldRouteToSpecialPlanEligibilityEngine() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "NATIONAL_SPECIAL");

        assertThat(decision.getEngineName()).isEqualTo(SpecialPlanEligibilityEngine.NAME);
        assertThat(decision.isQueryOnly()).isTrue();
        assertThat(decision.getRecommendMode()).isEqualTo("ELIGIBILITY_QUERY");
    }

    @Test
    void unsupportedBatch_shouldNeverFallbackToNormalUndergraduate() {
        RecommendEngineDecision decision = router.resolve("GZ", "普通类", "UNKNOWN_BATCH");

        assertThat(decision.getEngineName()).isEqualTo(QueryOnlyRecommendEngine.NAME);
        assertThat(decision.getSupportLevel()).isEqualTo("UNSUPPORTED");
        assertThat(decision.getBatchCode()).isEqualTo("UNKNOWN_BATCH");
        assertThat(decision.getEngineName()).isNotEqualTo(OrdinaryParallelMajorEngine.NAME);
    }
}
