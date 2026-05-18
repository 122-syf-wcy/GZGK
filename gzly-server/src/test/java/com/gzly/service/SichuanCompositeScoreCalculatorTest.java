package com.gzly.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SichuanCompositeScoreCalculatorTest {

    private final SichuanCompositeScoreCalculator calc = new SichuanCompositeScoreCalculator();

    @Test
    void art5050_meishu_shouldFollowFormula() {
        // 美术与设计类：文化 480，统考 280
        // 综合 = 480×0.5 + 280×(750/300)×0.5 = 240 + 350 = 590
        Optional<SichuanCompositeScoreCalculator.CompositeScoreResult> result =
                calc.calculateArt(480, 280, "美术与设计类");
        assertThat(result).isPresent();
        SichuanCompositeScoreCalculator.CompositeScoreResult r = result.get();
        assertThat(r.getScore()).isEqualTo(590.0);
        assertThat(r.getCultureRatio()).isEqualTo(0.5);
        assertThat(r.getProfessionalRatio()).isEqualTo(0.5);
        assertThat(r.getCategory()).contains("美术");
        assertThat(r.getFormula()).contains("480 × 50%").contains("280 × (750/300) × 50%");
    }

    @Test
    void art5050_bian_dao_biao_yan_dao_yan_fu_zhuang_bo_yin() {
        // 全部 50/50 类别都应能识别
        for (String category : new String[]{
                "戏剧影视编导类", "戏剧影视表演类", "戏剧影视导演类",
                "服装表演类", "播音与主持类", "美术", "设计"}) {
            assertThat(calc.resolveArtCategory(category))
                    .as(category)
                    .contains(SichuanCompositeScoreCalculator.ArtSportsGroup.ART_50_50);
        }
    }

    @Test
    void art3070_yinyue_wudao_shufa_hangkong_shouldFollowFormula() {
        // 音乐表演类：文化 450，统考 250
        // 综合 = 450×0.3 + 250×(750/300)×0.7 = 135 + 437.5 = 572.5
        Optional<SichuanCompositeScoreCalculator.CompositeScoreResult> result =
                calc.calculateArt(450, 250, "音乐表演类");
        assertThat(result).isPresent();
        SichuanCompositeScoreCalculator.CompositeScoreResult r = result.get();
        assertThat(r.getScore()).isEqualTo(572.5);
        assertThat(r.getCultureRatio()).isEqualTo(0.3);
        assertThat(r.getProfessionalRatio()).isEqualTo(0.7);

        for (String category : new String[]{
                "音乐教育类", "舞蹈类", "书法类", "航空服务艺术类"}) {
            assertThat(calc.resolveArtCategory(category))
                    .as(category)
                    .contains(SichuanCompositeScoreCalculator.ArtSportsGroup.ART_30_70);
        }
    }

    @Test
    void sports_shouldFollow3070() {
        // 体育：文化 450，体育统考 88
        // 综合 = 450×0.3 + 88×(750/100)×0.7 = 135 + 462 = 597
        SichuanCompositeScoreCalculator.CompositeScoreResult result = calc.calculateSports(450, 88);
        assertThat(result.getScore()).isEqualTo(597.0);
        assertThat(result.getCultureRatio()).isEqualTo(0.3);
        assertThat(result.getProfessionalRatio()).isEqualTo(0.7);
        assertThat(result.getCategory()).contains("体育");
        assertThat(result.getFormula()).contains("× (750/100)");
    }

    @Test
    void unifiedEntry_shouldRouteByCandidateType() {
        // 艺术
        Optional<SichuanCompositeScoreCalculator.CompositeScoreResult> art =
                calc.calculate("艺术类", 480, 280, "美术与设计类");
        assertThat(art).isPresent();
        assertThat(art.get().getScore()).isEqualTo(590.0);

        // 体育（artCategory 可为空）
        Optional<SichuanCompositeScoreCalculator.CompositeScoreResult> sports =
                calc.calculate("体育类", 450, 88, null);
        assertThat(sports).isPresent();
        assertThat(sports.get().getScore()).isEqualTo(597.0);

        // 普通类不计算综合分
        assertThat(calc.calculate("普通类", 600, 0, null)).isEmpty();
    }

    @Test
    void unknownCategory_shouldReturnEmpty() {
        assertThat(calc.calculateArt(480, 280, "未知类别")).isEmpty();
        assertThat(calc.calculateArt(480, 280, "")).isEmpty();
        assertThat(calc.calculateArt(480, 280, null)).isEmpty();
    }

    @Test
    void scoresClampedToValidRange() {
        // 文化 1000 被截到 750，统考 500 被截到 300
        Optional<SichuanCompositeScoreCalculator.CompositeScoreResult> r =
                calc.calculateArt(1000, 500, "美术与设计类");
        assertThat(r).isPresent();
        // 综合 = 750×0.5 + 300×2.5×0.5 = 375 + 375 = 750
        assertThat(r.get().getScore()).isEqualTo(750.0);
        assertThat(r.get().getCultureScore()).isEqualTo(750);
        assertThat(r.get().getProfessionalScore()).isEqualTo(300);
    }

    @Test
    void listArtCategories_shouldCoverAll11() {
        assertThat(calc.listArtCategories()).hasSize(11);
        assertThat(calc.listArtCategories()).contains("美术与设计类", "音乐表演类", "舞蹈类", "书法类",
                "航空服务艺术类", "播音与主持类", "服装表演类", "戏剧影视编导类", "戏剧影视表演类",
                "戏剧影视导演类", "音乐教育类");
    }

    @Test
    void formulaTextShouldBeHumanReadable() {
        SichuanCompositeScoreCalculator.CompositeScoreResult r =
                calc.calculateArt(480, 280, "美术与设计类").orElseThrow();
        // 期望格式：综合 = 文化 480 × 50% + 统考 280 × (750/300) × 50% = 240 + 350 = 590.00
        assertThat(r.getFormula())
                .startsWith("综合 = 文化 480 × 50%")
                .contains("统考 280 × (750/300) × 50%")
                .contains("240")
                .contains("350")
                .endsWith("590.00");
    }
}
