package com.gzly.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * AnhuiCompositeScoreCalculator 单测。
 *
 * <p>口径来源：皖招委〔2024〕11 号 + 安徽 2025 体育文化控线公告。</p>
 */
class AnhuiCompositeScoreCalculatorTest {

    private final AnhuiCompositeScoreCalculator calc = new AnhuiCompositeScoreCalculator();

    @Test
    void calculateArt_meishuShouldUse5050() {
        // 综合 = 480 × 0.5 + 280 × 2.5 × 0.5 = 240 + 350 = 590
        Optional<AnhuiCompositeScoreCalculator.CompositeScoreResult> res = calc.calculateArt(480, 280, "美术与设计类");
        assertThat(res).isPresent();
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = res.orElseThrow();
        assertThat(r.getScore()).isEqualTo(590.00);
        assertThat(r.getCultureRatio()).isEqualTo(0.5);
        assertThat(r.getProfessionalRatio()).isEqualTo(0.5);
        assertThat(r.getProfessionalScale()).isEqualTo(2.5);
        assertThat(r.getCategory()).contains("综合分1");
        assertThat(r.getFormula()).contains("文化 480 × 50% + 统考 280 × (750/300) × 50%");
    }

    @Test
    void calculateArt_yinyueShouldUse5050() {
        // 综合 = 500 × 0.5 + 260 × 2.5 × 0.5 = 250 + 325 = 575
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateArt(500, 260, "音乐表演类").orElseThrow();
        assertThat(r.getScore()).isEqualTo(575.00);
        assertThat(r.getCultureRatio()).isEqualTo(0.5);
    }

    @Test
    void calculateArt_wudaoShouldUse5050() {
        // 综合 = 460 × 0.5 + 240 × 2.5 × 0.5 = 230 + 300 = 530
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateArt(460, 240, "舞蹈类").orElseThrow();
        assertThat(r.getScore()).isEqualTo(530.00);
    }

    @Test
    void calculateArt_biaoyanShouldUse5050() {
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateArt(470, 250, "表（导）演类").orElseThrow();
        assertThat(r.getCultureRatio()).isEqualTo(0.5);
        assertThat(r.getScore()).isEqualTo(547.5);
    }

    @Test
    void calculateArt_shufaShouldUse5050() {
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateArt(490, 220, "书法类").orElseThrow();
        assertThat(r.getCultureRatio()).isEqualTo(0.5);
        // 综合 = 490 × 0.5 + 220 × 2.5 × 0.5 = 245 + 275 = 520
        assertThat(r.getScore()).isEqualTo(520.0);
    }

    @Test
    void calculateArt_boyinShouldUse7030() {
        // 综合 = 520 × 0.7 + 250 × 2.5 × 0.3 = 364 + 187.5 = 551.5
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateArt(520, 250, "播音与主持类").orElseThrow();
        assertThat(r.getScore()).isEqualTo(551.5);
        assertThat(r.getCultureRatio()).isEqualTo(0.7);
        assertThat(r.getProfessionalRatio()).isEqualTo(0.3);
        assertThat(r.getCategory()).contains("综合分2");
    }

    @Test
    void calculateArt_unknownCategoryShouldReturnEmpty() {
        Optional<AnhuiCompositeScoreCalculator.CompositeScoreResult> r = calc.calculateArt(500, 200, "不认识的类别");
        assertThat(r).isEmpty();
    }

    @Test
    void calculateSports_physicsShouldUse300Line() {
        // 综合 = 1.2 × 85 + 0.8 × [60 + 40 × (420 - 300) / (750 - 300)]
        //      = 102 + 0.8 × [60 + 40 × 120/450]
        //      = 102 + 0.8 × [60 + 10.6667]
        //      = 102 + 0.8 × 70.6667
        //      = 102 + 56.5333
        //      = 158.53
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateSports(420, 85, "物理");
        assertThat(r.getCultureBenkeLine()).isEqualTo(300);
        assertThat(r.getScore()).isEqualTo(158.53, within(0.02));
        assertThat(r.getCandidateType()).isEqualTo("体育类");
        assertThat(r.getFormula()).contains("1.2 × 专业 85");
        assertThat(r.getFormula()).contains("本科文化控线 300");
    }

    @Test
    void calculateSports_historyShouldUse310Line() {
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateSports(420, 85, "历史");
        assertThat(r.getCultureBenkeLine()).isEqualTo(310);
        // 综合 = 1.2 × 85 + 0.8 × [60 + 40 × (420 - 310)/(750 - 310)]
        //      = 102 + 0.8 × [60 + 40 × 110/440]
        //      = 102 + 0.8 × [60 + 10]
        //      = 102 + 56 = 158
        assertThat(r.getScore()).isEqualTo(158.0, within(0.01));
    }

    @Test
    void calculateSports_unknownSubjectFallsBackToPhysics() {
        AnhuiCompositeScoreCalculator.CompositeScoreResult r = calc.calculateSports(420, 85, null);
        assertThat(r.getCultureBenkeLine()).isEqualTo(300);
    }

    @Test
    void calculate_dispatchByCandidateType() {
        AnhuiCompositeScoreCalculator.CompositeScoreResult art = calc.calculate(
                "艺术类", 480, 280, "美术与设计类", null).orElseThrow();
        assertThat(art.getScore()).isEqualTo(590.00);

        AnhuiCompositeScoreCalculator.CompositeScoreResult sports = calc.calculate(
                "体育类", 420, 85, null, "物理").orElseThrow();
        assertThat(sports.getScore()).isEqualTo(158.53, within(0.02));

        Optional<AnhuiCompositeScoreCalculator.CompositeScoreResult> ordinary = calc.calculate(
                "普通类", 580, 0, null, "物理");
        assertThat(ordinary).isEmpty();
    }

    @Test
    void listArtCategoriesShouldReturnSevenCategories() {
        assertThat(calc.listArtCategories()).hasSize(7);
        assertThat(calc.listArtCategories()).contains("美术与设计类", "音乐表演类", "舞蹈类",
                "表（导）演类", "书法类", "播音与主持类");
    }
}
