package com.gzly.algorithm;

import com.gzly.service.VolunteerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VolunteerClosedLoopEngineTest {

    @Test
    void gradientAllocationShouldRespectPolicyMaxCountAndRiskPreference() {
        GradientAllocationEngine engine = new GradientAllocationEngine();

        assertThat(engine.allocate(96, "均衡型")).containsEntry("冲", 19).containsEntry("稳", 38)
                .containsEntry("保", 29).containsEntry("垫", 10);
        assertThat(engine.allocate(60, "保守型")).containsEntry("冲", 6).containsEntry("稳", 21)
                .containsEntry("保", 21).containsEntry("垫", 12);
        assertThat(engine.allocate(96, "冲刺型").get("冲"))
                .isGreaterThan(engine.allocate(96, "保守型").get("冲"));
        assertThat(engine.allocate(96, "保守型").get("垫"))
                .isGreaterThan(engine.allocate(96, "冲刺型").get("垫"));
        assertThat(engine.allocate(73, "均衡型").values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(73);
    }

    @Test
    void candidateFilterShouldRejectHardRuleViolationsBeforePrediction() {
        CandidateFilterEngine engine = new CandidateFilterEngine();
        CandidateFilterEngine.FilterCriteria criteria = new CandidateFilterEngine.FilterCriteria();
        criteria.setYear(2026);
        criteria.setProvince("GZ");
        criteria.setBatchCode("NORMAL_UNDERGRADUATE");
        criteria.setCandidateType("普通类");
        criteria.setSubjectType("物理类");
        criteria.setSelectedSubjects(List.of("物理", "化学", "生物"));
        criteria.setMaxTuition(12000);
        criteria.setAcceptPrivateSchool(false);
        criteria.setAcceptChineseForeignCoop(false);
        criteria.setDislikedMajors(List.of("护理", "土木"));
        criteria.setMedicalLimitations(List.of("色弱"));
        criteria.setSingleSubjectScores(Map.of("数学", 80));

        CandidateFilterEngine.CandidatePlan ok = plan("计算机科学与技术", 9000, false, false, "物理 化学", "");
        CandidateFilterEngine.CandidatePlan bad = plan("土木工程", 18000, true, true, "物理 化学", "色弱慎报，数学不低于100");

        CandidateFilterEngine.FilterResult result = engine.filterCandidates(criteria, List.of(ok, bad));

        assertThat(result.getAccepted()).containsExactly(ok);
        assertThat(result.getRejected()).containsExactly(bad);
        assertThat(bad.getFilterReasons()).contains("学费超过上限", "用户不接受民办", "用户不接受中外合作", "命中排斥专业", "体检限制不匹配", "单科成绩限制不满足");
    }

    @Test
    void fallbackPredictionShouldUseRankDiffPlanAndDataConfidence() {
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine();

        FallbackRulePredictionEngine.Prediction weak = engine.predict(32000, 28000, 0.05, 0.10, 0.90, 0);
        FallbackRulePredictionEngine.Prediction strong = engine.predict(32000, 42000, 0.05, 0.10, 0.90, 0);
        FallbackRulePredictionEngine.Prediction shrinking = engine.predict(32000, 42000, 0.05, -0.25, 0.90, 0);
        FallbackRulePredictionEngine.Prediction lowData = engine.predict(32000, 42000, 0.05, 0.10, 0.40, 0);

        assertThat(strong.getChanceScore()).isGreaterThan(weak.getChanceScore());
        assertThat(shrinking.getChanceScore()).isLessThan(strong.getChanceScore());
        assertThat(lowData.getConfidenceLevel()).isEqualTo("数据不足");
        assertThat(lowData.getDataConfidence()).isLessThan(55);
    }

    @Test
    void featureBuilderShouldComputeRankStatisticsAndDataConfidence() {
        FeatureBuildEngine.RankFeature full = new FeatureBuildEngine()
                .buildRankFeatures(List.of(23000, 25000, 24000), List.of(12, 10, 10));
        FeatureBuildEngine.RankFeature sparse = new FeatureBuildEngine()
                .buildRankFeatures(List.of(23000), List.of());

        assertThat(full.getLatestMinRank()).isEqualTo(23000);
        assertThat(full.getMedianMinRank3y()).isEqualTo(24000);
        assertThat(full.getPlanChangeRate()).isEqualTo(0.2);
        assertThat(full.getDataConfidence()).isGreaterThan(sparse.getDataConfidence());
        assertThat(sparse.getConfidenceLevel()).isEqualTo("低");
    }

    @Test
    void sortAndDiagnosisShouldUseVisibleChanceRiskAndWarnings() {
        VolunteerService.VolunteerItem rush = item("冲", 42, 70, "贵阳", "计算机科学与技术");
        VolunteerService.VolunteerItem floor = item("垫", 92, 90, "贵阳", "软件工程");
        VolunteerService.VolunteerItem stable = item("稳", 65, 80, "成都", "电子信息工程");

        List<VolunteerService.VolunteerItem> sorted = new VolunteerSortEngine().sort(List.of(floor, stable, rush));
        assertThat(sorted).extracting(VolunteerService.VolunteerItem::getGradient).containsExactly("冲", "稳", "垫");
        assertThat(sorted).extracting(VolunteerService.VolunteerItem::getIndex).containsExactly(1, 2, 3);

        Map<String, Object> diagnosis = new VolunteerDiagnosisEngine().diagnose(sorted, 96);
        assertThat((List<String>) diagnosis.get("warnings"))
                .anyMatch(text -> text.contains("仅供辅助参考"));
        assertThat((Map<String, Long>) diagnosis.get("gradientCount"))
                .containsEntry("rush", 1L)
                .containsEntry("floor", 1L);
    }

    private CandidateFilterEngine.CandidatePlan plan(String major, int tuition, boolean privateSchool,
                                                     boolean coop, String requirement, String remarks) {
        CandidateFilterEngine.CandidatePlan plan = new CandidateFilterEngine.CandidatePlan();
        plan.setYear(2026);
        plan.setProvince("GZ");
        plan.setBatchCode("NORMAL_UNDERGRADUATE");
        plan.setCandidateType("普通类");
        plan.setSubjectType("物理类");
        plan.setMajorName(major);
        plan.setTuition(tuition);
        plan.setPrivateSchool(privateSchool);
        plan.setChineseForeignCoop(coop);
        plan.setSelectedSubjectRequirement(requirement);
        plan.setRemarks(remarks);
        plan.setSpecialLimit(remarks);
        return plan;
    }

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
