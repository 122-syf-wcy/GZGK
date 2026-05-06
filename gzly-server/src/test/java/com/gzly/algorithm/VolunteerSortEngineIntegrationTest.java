package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VolunteerSortEngine + PreferenceWeights.fromStrategyAndProfile 集成测试。
 *
 * <p>验证三件事：</p>
 * <ol>
 *   <li>整体志愿顺序固定为 冲 → 稳 → 保 → 垫，不随 strategyMode 变化；</li>
 *   <li>fromStrategyAndProfile 按 strategyMode/decisionPriority/careerGoal 微调权重；</li>
 *   <li>保守型在 finalScore 公式里放大 chance/confidence/risk 系数。</li>
 * </ol>
 */
class VolunteerSortEngineIntegrationTest {

    private final VolunteerSortEngine engine = new VolunteerSortEngine();

    @Test
    void sort_keepsGradientOrderRegardlessOfStrategyMode() {
        List<VolunteerService.VolunteerItem> items = sampleMixedItems();
        for (String mode : List.of("保守型", "均衡型", "冲刺型")) {
            VolunteerSortEngine.PreferenceWeights w = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                    mode, "专业优先", "就业优先", null, null, null, null);
            List<VolunteerService.VolunteerItem> sorted = engine.sort(new ArrayList<>(items), w);
            assertThat(extractGradientSequence(sorted))
                    .as("strategyMode=%s 整体顺序应固定为 冲→稳→保→垫", mode)
                    .containsExactly("冲", "冲", "稳", "稳", "保", "保", "垫", "垫");
        }
    }

    @Test
    void fromStrategyAndProfile_decisionPriority_swapsMajorAndSchoolWeights() {
        VolunteerSortEngine.PreferenceWeights schoolFirst = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "均衡型", "学校优先", null, null, null, null, null);
        VolunteerSortEngine.PreferenceWeights majorFirst = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "均衡型", "专业优先", null, null, null, null, null);
        // 学校优先时 schoolPriority > majorPriority；专业优先反之
        assertThat(schoolFirst.getSchoolPriority()).isGreaterThan(schoolFirst.getMajorPriority());
        assertThat(majorFirst.getMajorPriority()).isGreaterThan(majorFirst.getSchoolPriority());
        // 归一化后总和应 ≈ 1.0
        double sum = schoolFirst.getMajorPriority() + schoolFirst.getSchoolPriority()
                + schoolFirst.getCityPriority() + schoolFirst.getEmploymentPriority();
        assertThat(sum).isCloseTo(1.0D, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void fromStrategyAndProfile_aggressiveMode_amplifiesMajorAndSchool() {
        VolunteerSortEngine.PreferenceWeights aggressive = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "冲刺型", null, null, null, null, null, null);
        VolunteerSortEngine.PreferenceWeights balanced = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "均衡型", null, null, null, null, null, null);
        // 冲刺型应该给 major/school/city 更高权重，employment 更低
        assertThat(aggressive.getMajorPriority()).isGreaterThan(balanced.getMajorPriority());
        assertThat(aggressive.getEmploymentPriority()).isLessThan(balanced.getEmploymentPriority());
    }

    @Test
    void finalScore_conservativeMode_amplifiesChanceCoefficient() {
        VolunteerService.VolunteerItem item = volunteerItem("稳", 80, 70.0D, "中等");
        VolunteerSortEngine.PreferenceWeights conservative = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "保守型", null, null, null, null, null, null);
        VolunteerSortEngine.PreferenceWeights balanced = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                "均衡型", null, null, null, null, null, null);

        double conservativeScore = engine.finalScore(item, conservative);
        // 重置 recommendationScore 副作用，避免影响下一次比较
        item.setRecommendationScore(0);
        double balancedScore = engine.finalScore(item, balanced);

        // chance=80 时保守型 0.45 系数 > 均衡型 0.35 系数；conservativeScore 应明显更大
        assertThat(conservativeScore).isGreaterThan(balancedScore);
    }

    @Test
    void weights_normalized_preservesStrategyMode() {
        VolunteerSortEngine.PreferenceWeights w = new VolunteerSortEngine.PreferenceWeights(0.4D, 0.4D, 0.0D, 0.2D);
        w.setStrategyMode("冲刺型");
        VolunteerSortEngine.PreferenceWeights normalized = w.normalized();
        assertThat(normalized.getStrategyMode()).isEqualTo("冲刺型");
    }

    // ───────────── helpers ─────────────

    private List<VolunteerService.VolunteerItem> sampleMixedItems() {
        return List.of(
                volunteerItem("垫", 30, 60.0D, "较高"),
                volunteerItem("冲", 35, 65.0D, "较高"),
                volunteerItem("保", 70, 80.0D, "较低"),
                volunteerItem("稳", 60, 85.0D, "中等"),
                volunteerItem("垫", 25, 55.0D, "较高"),
                volunteerItem("冲", 32, 70.0D, "较高"),
                volunteerItem("保", 75, 78.0D, "较低"),
                volunteerItem("稳", 65, 82.0D, "中等")
        );
    }

    private VolunteerService.VolunteerItem volunteerItem(String gradient, int chance, double dataConfidence, String risk) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setGradient(gradient);
        item.setChanceScore(chance);
        item.setDataConfidence(dataConfidence);
        item.setRiskLevel(risk);
        item.setMatchScore(50);
        item.setUniversityName("测试大学");
        item.setMajorName("测试专业");
        return item;
    }

    private List<String> extractGradientSequence(List<VolunteerService.VolunteerItem> items) {
        List<String> seq = new ArrayList<>();
        for (VolunteerService.VolunteerItem item : items) {
            seq.add(item.getGradient());
        }
        return seq;
    }
}
