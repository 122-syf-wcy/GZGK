package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2 算法层增强单测：
 * - RankFeature 新增 firstRoundFull / hasSupplement
 * - VolunteerSortEngine 用户偏好权重
 * - VolunteerDiagnosisEngine 新增 学校层次断档 / 高风险连堆 / 用户排斥专业残留
 */
class AlgorithmEnhancementTest {

    // ---- FeatureBuildEngine ------------------------------------------------

    @Test
    void rankFeatureShouldExposeSupplementAndFirstRoundFlags() {
        FeatureBuildEngine engine = new FeatureBuildEngine();

        FeatureBuildEngine.RankFeature feature = engine.buildRankFeatures(
                List.of(23000, 25000, 24000),
                List.of(12, 10, 10),
                List.of(true, true, false),
                List.of(false, false, true));

        assertThat(feature.isFirstRoundFull()).isTrue();
        assertThat(feature.isHasSupplement()).isFalse();
        assertThat(feature.getDataMissingCount()).isZero();
    }

    @Test
    void rankFeatureSupplementFlagsHandleNullsAndAllNulls() {
        FeatureBuildEngine engine = new FeatureBuildEngine();

        FeatureBuildEngine.RankFeature mixed = engine.buildRankFeatures(
                List.of(23000),
                List.of(),
                Arrays.asList(null, true, false),
                Arrays.asList(null, null, true));
        assertThat(mixed.isFirstRoundFull()).isTrue();
        assertThat(mixed.isHasSupplement()).isTrue();

        FeatureBuildEngine.RankFeature empty = engine.buildRankFeatures(
                List.of(), List.of(), null, null);
        assertThat(empty.isFirstRoundFull()).isFalse();
        assertThat(empty.isHasSupplement()).isFalse();
        assertThat(empty.getDataMissingCount()).isEqualTo(3);
        assertThat(empty.getConfidenceLevel()).isEqualTo("数据不足");
    }

    // ---- VolunteerSortEngine + PreferenceWeights ---------------------------

    @Test
    void preferenceWeightsShouldNormalizeAndFallbackToDefaults() {
        VolunteerSortEngine.PreferenceWeights w = VolunteerSortEngine.PreferenceWeights.of(
                0.6, 0.2, 0.2, 0.0);
        assertThat(w.getMajorPriority() + w.getSchoolPriority()
                + w.getCityPriority() + w.getEmploymentPriority())
                .isEqualTo(1.0);

        VolunteerSortEngine.PreferenceWeights zero = new VolunteerSortEngine.PreferenceWeights(0, 0, 0, 0).normalized();
        assertThat(zero).usingRecursiveComparison().isEqualTo(VolunteerSortEngine.PreferenceWeights.defaults());
    }

    @Test
    void sortShouldRespectMajorWeightForCityVsMajorTradeoff() {
        VolunteerSortEngine sort = new VolunteerSortEngine();
        VolunteerService.VolunteerItem majorMatch = item("稳", 65, 80, "上海", "电子信息工程");
        majorMatch.setMatchTag("专业");
        majorMatch.setMatchScore(95);
        VolunteerService.VolunteerItem cityMatch = item("稳", 65, 80, "贵阳", "工商管理");
        cityMatch.setMatchTag("地区");
        cityMatch.setMatchScore(70);

        // 偏专业：majorMatch 应该在前
        List<VolunteerService.VolunteerItem> majorFirst = sort.sort(
                List.of(cityMatch, majorMatch),
                VolunteerSortEngine.PreferenceWeights.of(0.7, 0.1, 0.1, 0.1));
        assertThat(majorFirst.get(0)).isSameAs(majorMatch);

        // 偏城市：cityMatch 应该在前
        List<VolunteerService.VolunteerItem> cityFirst = sort.sort(
                List.of(majorMatch, cityMatch),
                VolunteerSortEngine.PreferenceWeights.of(0.05, 0.05, 0.85, 0.05));
        assertThat(cityFirst.get(0)).isSameAs(cityMatch);
    }

    @Test
    void sortShouldKeepGradientPriorityRegardlessOfWeights() {
        VolunteerSortEngine sort = new VolunteerSortEngine();
        VolunteerService.VolunteerItem rush = item("冲", 40, 70, "贵阳", "计算机科学与技术");
        VolunteerService.VolunteerItem floor = item("垫", 95, 90, "贵阳", "软件工程");

        List<VolunteerService.VolunteerItem> sorted = sort.sort(List.of(floor, rush),
                VolunteerSortEngine.PreferenceWeights.of(0.0, 0.0, 1.0, 0.0));

        // 即使把所有权重压到城市，冲一定排在垫前面（梯度优先级硬约束）
        assertThat(sorted.get(0).getGradient()).isEqualTo("冲");
        assertThat(sorted.get(1).getGradient()).isEqualTo("垫");
    }

    // ---- VolunteerDiagnosisEngine 新维度 -----------------------------------

    @Test
    void diagnosisShouldFlagConsecutiveHighRiskRun() {
        VolunteerDiagnosisEngine engine = new VolunteerDiagnosisEngine();
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        // 6 条连续 chance < 50
        for (int i = 0; i < 6; i++) items.add(item("冲", 30 + i, 70, "贵阳", "电子" + i));
        // 几条稳
        for (int i = 0; i < 10; i++) items.add(item("稳", 65, 75, "贵阳", "稳" + i));
        // 几条垫
        for (int i = 0; i < 8; i++) items.add(item("垫", 92, 80, "贵阳", "垫" + i));

        Map<String, Object> diag = engine.diagnose(items, 96);

        @SuppressWarnings("unchecked")
        List<String> diagnosis = (List<String>) diag.get("diagnosis");
        assertThat(diag.get("longestHighRiskRun")).isEqualTo(6);
        assertThat(diagnosis).anyMatch(t -> t.contains("高风险项连续堆叠"));
        assertThat(diag.get("overallRisk")).isEqualTo("中等偏高");
    }

    @Test
    void diagnosisShouldFlagSchoolTierGap() {
        VolunteerDiagnosisEngine engine = new VolunteerDiagnosisEngine();
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            VolunteerService.VolunteerItem it = item("稳", 65, 75, "贵阳", "工商" + i);
            it.setTags(List.of("普通本科")); // 没有 985/211/双一流
            items.add(it);
        }
        Map<String, Object> diag = engine.diagnose(items, 96);

        @SuppressWarnings("unchecked")
        List<String> diagnosis = (List<String>) diag.get("diagnosis");
        assertThat(diag.get("eliteCount")).isEqualTo(0L);
        assertThat(diagnosis).anyMatch(t -> t.contains("985") && t.contains("层次梯度"));
    }

    @Test
    void diagnosisShouldFlagDislikedMajorResidue() {
        VolunteerDiagnosisEngine engine = new VolunteerDiagnosisEngine();
        List<VolunteerService.VolunteerItem> items = List.of(
                item("稳", 65, 75, "贵阳", "护理学"),
                item("稳", 65, 75, "贵阳", "土木工程"),
                item("稳", 65, 75, "贵阳", "计算机科学与技术"));
        VolunteerDiagnosisEngine.DiagnosisContext ctx = new VolunteerDiagnosisEngine.DiagnosisContext();
        ctx.setDislikedMajors(List.of("护理", "土木"));

        Map<String, Object> diag = engine.diagnose(items, 96, ctx);

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) diag.get("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("护理") && w.contains("土木") && w.contains("人工复核"));
    }

    @Test
    void diagnosisWithEliteSchoolsShouldNotFlagTierGap() {
        VolunteerDiagnosisEngine engine = new VolunteerDiagnosisEngine();
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            VolunteerService.VolunteerItem it = item("稳", 65, 75, "上海", "X" + i);
            it.setTags(List.of("普通本科"));
            items.add(it);
        }
        VolunteerService.VolunteerItem elite = item("冲", 40, 80, "北京", "顶尖");
        elite.setTags(List.of("985", "双一流"));
        items.add(elite);

        Map<String, Object> diag = engine.diagnose(items, 96);
        assertThat(diag.get("eliteCount")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<String> diagnosis = (List<String>) diag.get("diagnosis");
        assertThat(diagnosis).noneMatch(t -> t.contains("缺少 985"));
    }

    // ---- helper -----------------------------------------------------------

    private VolunteerService.VolunteerItem item(String gradient, int chanceScore, double dataConfidence,
                                                String city, String major) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setGradient(gradient);
        item.setChanceScore(chanceScore);
        item.setDataConfidence(dataConfidence);
        item.setRiskLevel(chanceScore < 50 ? "较高" : chanceScore < 75 ? "中等" : "较低");
        item.setCity(city);
        item.setMajorName(major);
        item.setUniversityName(city + "大学");
        item.setPlanTrend("基本稳定");
        return item;
    }
}
