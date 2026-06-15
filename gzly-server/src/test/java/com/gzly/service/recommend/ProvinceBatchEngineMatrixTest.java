package com.gzly.service.recommend;

import com.gzly.service.AnhuiBatchRuleRegistry;
import com.gzly.service.BatchRuleRegistry;
import com.gzly.service.HubeiBatchRuleRegistry;
import com.gzly.service.NextProvincePolicyRegistry;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.SichuanBatchRuleRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 (province, batch) → engineName 映射的覆盖完整性 + 路由正确性 + self-check 全过。
 */
class ProvinceBatchEngineMatrixTest {

    @Test
    void resolveEngineName_guizhouMainStreamShouldReturnOrdinaryParallel() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "NORMAL_UNDERGRADUATE"))
                .isEqualTo(OrdinaryParallelMajorEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "NORMAL_SPECIALTY"))
                .isEqualTo(OrdinaryParallelMajorEngine.NAME);
    }

    @Test
    void resolveEngineName_guizhouEarlyAbShouldReturnSequential() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "EARLY_A_B"))
                .isEqualTo(SequentialCollegeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "SPECIALTY_EARLY"))
                .isEqualTo(SequentialCollegeEngine.NAME);
    }

    @Test
    void resolveEngineName_guizhouEarlyCShouldReturnParallelMajor() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "EARLY_C"))
                .isEqualTo(EarlyCParallelMajorEngine.NAME);
    }

    @Test
    void resolveEngineName_guizhouArtSportsSpecialShouldUseDedicatedEngines() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "ART_UNDERGRADUATE_A"))
                .isEqualTo(ArtCompositeRecommendEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "SPORTS_UNDERGRADUATE"))
                .isEqualTo(SportsCompositeRecommendEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("GZ", "NATIONAL_SPECIAL"))
                .isEqualTo(SpecialPlanEligibilityEngine.NAME);
    }

    @Test
    void resolveEngineName_sichuanMainStreamShouldReturnSichuanPg45() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_BENKE_B"))
                .isEqualTo(SichuanProfessionalGroup45Engine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_ZHUANKE_B"))
                .isEqualTo(SichuanProfessionalGroup45Engine.NAME);
        // 提前批 B 段 30 平行院校专业组也走主流程引擎（同口径）
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_TIQIAN_B"))
                .isEqualTo(SichuanProfessionalGroup45Engine.NAME);
    }

    @Test
    void resolveEngineName_sichuanArtSportsSequentialSpecial() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_ART_BENKE"))
                .isEqualTo(SichuanArtCompositeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_SPORTS_BENKE"))
                .isEqualTo(SichuanSportsCompositeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_TIQIAN_A"))
                .isEqualTo(SichuanSequentialCollegeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "SC_BENKE_A_NATIONAL"))
                .isEqualTo(SichuanSpecialPlanEligibilityEngine.NAME);
    }

    @Test
    void resolveEngineName_anhuiMainStreamShouldReturnAnhuiPg45() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_BENKE"))
                .isEqualTo(AnhuiProfessionalGroup45Engine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_ZHUANKE"))
                .isEqualTo(AnhuiProfessionalGroup45Engine.NAME);
    }

    @Test
    void resolveEngineName_anhuiArtSportsSequentialSpecial() {
        // 艺术校考是 1 顺序 → SequentialCollege；艺术统考本科/专科是 20 平行综合分 → ArtComposite
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_ART_XIAOKAO_BENKE"))
                .isEqualTo(AnhuiSequentialCollegeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_ART_TONGKAO_BENKE"))
                .isEqualTo(AnhuiArtCompositeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_ART_TONGKAO_ZHUANKE"))
                .isEqualTo(AnhuiArtCompositeEngine.NAME);
        // 体育本科 / 专科 → AnhuiSportsComposite
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_SPORTS_BENKE"))
                .isEqualTo(AnhuiSportsCompositeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_SPORTS_ZHUANKE"))
                .isEqualTo(AnhuiSportsCompositeEngine.NAME);
        // 提前批顺序 / 高校专项顺序 → AnhuiSequentialCollege
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_TIQIAN_BENKE_SEQUENTIAL"))
                .isEqualTo(AnhuiSequentialCollegeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_TIQIAN_ZHUANKE_SEQUENTIAL"))
                .isEqualTo(AnhuiSequentialCollegeEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_UNIVERSITY_SPECIAL"))
                .isEqualTo(AnhuiSequentialCollegeEngine.NAME);
        // 提前批平行 / 国家专项 / 地方专项 → AnhuiSpecialPlan
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_TIQIAN_BENKE_PARALLEL"))
                .isEqualTo(AnhuiSpecialPlanEligibilityEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_TIQIAN_ZHUANKE_PARALLEL"))
                .isEqualTo(AnhuiSpecialPlanEligibilityEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_NATIONAL_SPECIAL"))
                .isEqualTo(AnhuiSpecialPlanEligibilityEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("AH", "AH_LOCAL_SPECIAL"))
                .isEqualTo(AnhuiSpecialPlanEligibilityEngine.NAME);
    }

    @Test
    void resolveEngineName_hubeiShouldUseHbPrefixedBatches() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("HB", "HB_BENKE"))
                .isEqualTo("HubeiProfessionalGroup45Engine");
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("HB", "HB_EARLY"))
                .isEqualTo(QueryOnlyRecommendEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.entriesForProvince("HB"))
                .containsEntry("HB_BENKE", "HubeiProfessionalGroup45Engine")
                .doesNotContainKey("SC_BENKE_B");
    }

    @Test
    void resolveEngineName_unknownProvinceOrBatchFallsBackToQueryOnly() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("XYZ", "AH_BENKE"))
                .isEqualTo(QueryOnlyRecommendEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("SC", "NOT_A_REAL_BATCH"))
                .isEqualTo(QueryOnlyRecommendEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName(null, null))
                .isEqualTo(QueryOnlyRecommendEngine.NAME);
    }

    @Test
    void resolveEngineName_nextProvincesShouldHaveExplicitQueryOnlyBatches() {
        for (NextProvincePolicyRegistry.Profile profile : NextProvincePolicyRegistry.allProfiles()) {
            assertThat(ProvinceBatchEngineMatrix.resolveEngineName(profile.provinceCode(), profile.defaultBatchCode()))
                    .isEqualTo(QueryOnlyRecommendEngine.NAME);
            assertThat(ProvinceBatchEngineMatrix.entriesForProvince(profile.provinceCode()))
                    .containsEntry(profile.defaultBatchCode(), QueryOnlyRecommendEngine.NAME);
            assertThat(ProvinceBatchEngineMatrix.entriesForProvince(profile.provinceCode()).size())
                    .isEqualTo(profile.batches().size());
        }
    }

    @Test
    void resolveEngineName_provinceCodeNormalizationCaseInsensitive() {
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName("gz", "NORMAL_UNDERGRADUATE"))
                .isEqualTo(OrdinaryParallelMajorEngine.NAME);
        assertThat(ProvinceBatchEngineMatrix.resolveEngineName(" sc ", "SC_BENKE_B"))
                .isEqualTo(SichuanProfessionalGroup45Engine.NAME);
    }

    @Test
    void entriesForProvince_shouldListAllRegisteredBatchesForGivenProvince() {
        Map<String, String> ah = ProvinceBatchEngineMatrix.entriesForProvince("AH");
        assertThat(ah).hasSize(14);
        assertThat(ah).containsEntry("AH_BENKE", AnhuiProfessionalGroup45Engine.NAME);
        assertThat(ah).containsEntry("AH_ART_TONGKAO_BENKE", AnhuiArtCompositeEngine.NAME);

        Map<String, String> sc = ProvinceBatchEngineMatrix.entriesForProvince("SC");
        assertThat(sc).hasSize(18);
        assertThat(sc).containsEntry("SC_BENKE_B", SichuanProfessionalGroup45Engine.NAME);

        Map<String, String> hb = ProvinceBatchEngineMatrix.entriesForProvince("HB");
        assertThat(hb).hasSize(6);
        assertThat(hb).containsEntry("HB_BENKE", "HubeiProfessionalGroup45Engine");

        Map<String, String> gz = ProvinceBatchEngineMatrix.entriesForProvince("GZ");
        assertThat(gz).hasSize(18);
        assertThat(gz).containsEntry("NORMAL_UNDERGRADUATE", OrdinaryParallelMajorEngine.NAME);
    }

    @Test
    void selfCheck_shouldVerifyAllRegistriesMappedAndAllMappingsKnown() {
        ProvinceBatchEngineMatrix.SelfCheckResult result = ProvinceBatchEngineMatrix.selfCheck();
        assertThat(result.unknownBatches)
                .as("矩阵注册的批次必须在对应 Registry 中存在")
                .isEmpty();
        assertThat(result.unregisteredBatches)
                .as("Registry 中的每个批次必须在矩阵中注册（防止漂移）")
                .isEmpty();
        assertThat(result.ok()).isTrue();
    }

    @Test
    void totalRegistrations_shouldEqualSumOfThreeRegistries() {
        int expected = BatchRuleRegistry.allRules().size()
                + SichuanBatchRuleRegistry.allRules().size()
                + HubeiBatchRuleRegistry.allRules().size()
                + AnhuiBatchRuleRegistry.allRules().size()
                + NextProvincePolicyRegistry.allProfiles().stream().mapToInt(profile -> profile.batches().size()).sum();
        assertThat(ProvinceBatchEngineMatrix.totalRegistrations()).isEqualTo(expected);
    }

    @Test
    void provincePolicyServiceConstants_matchMatrixKeys() {
        // 防回归：确保 ProvincePolicyService.GZ / SC / AH 三个常量仍然是矩阵的 province key
        assertThat(ProvincePolicyService.GZ).isEqualTo("GZ");
        assertThat(ProvincePolicyService.SC).isEqualTo("SC");
        assertThat(ProvincePolicyService.AH).isEqualTo("AH");
        assertThat(ProvincePolicyService.GX).isEqualTo("GX");
        assertThat(ProvincePolicyService.HI).isEqualTo("HI");
        assertThat(ProvincePolicyService.YN).isEqualTo("YN");
        assertThat(ProvincePolicyService.HA).isEqualTo("HA");
    }
}
