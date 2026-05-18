package com.gzly.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SichuanBatchRuleRegistryTest {

    @Test
    void allRules_shouldContain18BatchesAcross6Categories() {
        // 四川 2026 共 18 批次：12 平行（含国家专项前 A 段 6 / 提前 B 30 / 本科 A 国家+地方专项各 20 /
        // 本科 A 段后高校专项 20 / B 段 45 / B 段后区域均衡 20 / B 段后少民预科 20 / 高职专科 45 /
        // 艺术本科 45 / 艺术高职 45 / 体育本科 45 / 体育高职 45）+ 6 顺序（提前 A / 提前批高校专项 /
        // 本科批高水平运动队 / 高职专科提前 / 艺术本科提前）。
        List<SichuanBatchRuleRegistry.BatchRule> rules = SichuanBatchRuleRegistry.allRules();
        assertThat(rules).hasSize(18);
        assertThat(rules.stream().map(SichuanBatchRuleRegistry.BatchRule::batchCode).toList()).contains(
                "SC_TIQIAN_BEFORE_A_NATIONAL", "SC_TIQIAN_A", "SC_GAOXIAO_SPECIAL_PRE_B", "SC_TIQIAN_B",
                "SC_BENKE_A_NATIONAL", "SC_BENKE_A_LOCAL", "SC_BENKE_GAOXIAO_SPECIAL", "SC_BENKE_SPORTS_TEAM",
                "SC_BENKE_B", "SC_BENKE_REGION_BALANCE", "SC_BENKE_MINORITY_PRE",
                "SC_ZHUANKE_B", "SC_ZHUANKE_EARLY",
                "SC_ART_TIQIAN", "SC_ART_BENKE", "SC_ART_ZHUANKE",
                "SC_SPORTS_BENKE", "SC_SPORTS_ZHUANKE");
    }

    @Test
    void mainStreamBenkeB_shouldUse45ParallelGroupsAnd6MajorsPerGroup() {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_BENKE_B");
        assertThat(rule.targetCount()).isEqualTo(45);
        assertThat(rule.majorsPerGroup()).isEqualTo(6);
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP);
        assertThat(rule.candidateType()).isEqualTo("普通类");
        assertThat(rule.category()).isEqualTo(SichuanBatchRuleRegistry.CandidateCategory.ORDINARY);
        assertThat(rule.mainRankEngine()).isTrue();
        assertThat(rule.hasAdjustment()).isTrue();
    }

    @Test
    void tiqian2026Upgrade_nationalSpecial_shouldBe6Parallel() {
        // 2026 由 2 改 6 平行
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_TIQIAN_BEFORE_A_NATIONAL");
        assertThat(rule.targetCount()).isEqualTo(6);
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP);
    }

    @Test
    void tiqianA_shouldBeSequentialCollege1plus2() {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_TIQIAN_A");
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.SEQUENTIAL_COLLEGE);
        assertThat(rule.targetCount()).isEqualTo(3);
    }

    @Test
    void benkeAandLocalSpecial_shouldBe20Parallel() {
        assertThat(SichuanBatchRuleRegistry.require("SC_BENKE_A_NATIONAL").targetCount()).isEqualTo(20);
        assertThat(SichuanBatchRuleRegistry.require("SC_BENKE_A_LOCAL").targetCount()).isEqualTo(20);
    }

    @Test
    void benkeGaoxiaoSpecial2026_shouldBe20ParallelPerImplementation() {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_BENKE_GAOXIAO_SPECIAL");
        assertThat(rule.targetCount()).isEqualTo(20);
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP);
    }

    @Test
    void sportsTeam_shouldBe1SequentialOnly() {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_BENKE_SPORTS_TEAM");
        assertThat(rule.targetCount()).isEqualTo(1);
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.SEQUENTIAL_COLLEGE);
        assertThat(rule.mainRankEngine()).isFalse();
    }

    @Test
    void minorityPrep_shouldBe20Parallel() {
        SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require("SC_BENKE_MINORITY_PRE");
        assertThat(rule.targetCount()).isEqualTo(20);
        assertThat(rule.recommendMode()).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP);
        assertThat(rule.category()).isEqualTo(SichuanBatchRuleRegistry.CandidateCategory.SPECIAL_PROGRAM);
    }

    @Test
    void artAndSportsBatches_shouldBe45ParallelCompositeScore() {
        for (String code : List.of("SC_ART_BENKE", "SC_ART_ZHUANKE", "SC_SPORTS_BENKE", "SC_SPORTS_ZHUANKE")) {
            SichuanBatchRuleRegistry.BatchRule rule = SichuanBatchRuleRegistry.require(code);
            assertThat(rule.targetCount()).as(code).isEqualTo(45);
            assertThat(rule.recommendMode()).as(code).isEqualTo(SichuanBatchRuleRegistry.RecommendMode.PARALLEL_GROUP);
        }
        assertThat(SichuanBatchRuleRegistry.require("SC_ART_BENKE").candidateType()).isEqualTo("艺术类");
        assertThat(SichuanBatchRuleRegistry.require("SC_SPORTS_BENKE").candidateType()).isEqualTo("体育类");
    }

    @Test
    void normalizeBatchCode_shouldRouteCommonAliases() {
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("本科批B段")).isEqualTo("SC_BENKE_B");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("本科批 B段")).isEqualTo("SC_BENKE_B");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("普通本科批B段")).isEqualTo("SC_BENKE_B");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("艺术类本科批")).isEqualTo("SC_ART_BENKE");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("体育本科批")).isEqualTo("SC_SPORTS_BENKE");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("国家专项A段前")).isEqualTo("SC_TIQIAN_BEFORE_A_NATIONAL");
        assertThat(SichuanBatchRuleRegistry.normalizeBatchCode("少数民族预科")).isEqualTo("SC_BENKE_MINORITY_PRE");
    }

    @Test
    void normalizeCandidateType_shouldClassifyArtSportsOrdinary() {
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType("美术与设计类")).isEqualTo("艺术类");
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType("音乐表演")).isEqualTo("艺术类");
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType("体育")).isEqualTo("体育类");
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType("运动训练")).isEqualTo("体育类");
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType("普通类")).isEqualTo("普通类");
        assertThat(SichuanBatchRuleRegistry.normalizeCandidateType(null)).isEqualTo("普通类");
    }

    @Test
    void candidateTypeMatches_shouldHonorRuleCandidateType() {
        assertThat(SichuanBatchRuleRegistry.candidateTypeMatches("艺术类", "美术")).isTrue();
        assertThat(SichuanBatchRuleRegistry.candidateTypeMatches("普通类", "艺术类")).isFalse();
        assertThat(SichuanBatchRuleRegistry.candidateTypeMatches("体育类", "运动训练")).isTrue();
    }

    @Test
    void mainRankBatchCodes_shouldExcludeOrderedAndSportsTeam() {
        List<String> mainCodes = SichuanBatchRuleRegistry.mainRankBatchCodes();
        assertThat(mainCodes).contains("SC_BENKE_B", "SC_BENKE_A_NATIONAL", "SC_TIQIAN_B");
        // 高水平运动队是顺序志愿，不算主链路
        assertThat(mainCodes).doesNotContain("SC_BENKE_SPORTS_TEAM", "SC_TIQIAN_A", "SC_ZHUANKE_EARLY");
    }

    @Test
    void officialSourceText_shouldReference2026Rules() {
        assertThat(SichuanBatchRuleRegistry.OFFICIAL_SOURCE_TEXT).contains("四川 2026").contains("45 平行");
        assertThat(SichuanBatchRuleRegistry.OFFICIAL_SOURCE_TEXT).contains("位次优先、遵循志愿、一轮投档");
    }
}
