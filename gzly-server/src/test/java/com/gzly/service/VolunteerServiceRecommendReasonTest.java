package com.gzly.service;

import com.gzly.service.VolunteerService.PreferenceProfile;
import com.gzly.service.VolunteerService.VolunteerItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4 段式推荐解释回归测试（贵州研究报告 R6）：
 * <ol>
 *   <li>梯度定位：冲/稳/保/垫 任一桶 + 参考概率区间</li>
 *   <li>适配理由：偏好匹配、数据级别、计划趋势、决策倾向等正向信号</li>
 *   <li>风险信号：波动、缩招、低精度等负向信号</li>
 *   <li>可调节项：用户下一步可以调整的偏好维度</li>
 * </ol>
 * 段间用 {@code " | "} 连接，前缀固定，便于前端按片段裁剪展示。
 */
class VolunteerServiceRecommendReasonTest {

    @Test
    void recommendReasonShouldRenderAllFourSegmentsWithFixedPrefixes() {
        VolunteerItem item = newItem();
        item.setGradient("稳");
        item.setChanceScore(68);
        item.setMatchScore(72);
        item.setDataSourceType("专业级");
        item.setPlanTrend("扩招");
        item.setSchoolEnrollmentIndex(80);
        item.setPrecisionScore(85);
        item.setReferenceFitLevel("较高");
        item.setAdmissionProb(70);
        item.setLatestPlanCount(15);

        String reason = VolunteerService.buildRecommendReason(item, balancedProfile());

        String[] segments = reason.split(" \\| ");
        assertThat(segments).hasSize(4);
        assertThat(segments[0]).startsWith("梯度定位：").contains("稳").contains("68%");
        assertThat(segments[1]).startsWith("适配理由：").contains("扩招").contains("专业级");
        assertThat(segments[2]).startsWith("风险信号：").contains("暂未识别");
        assertThat(segments[3]).startsWith("可调节项：");
    }

    @Test
    void rushGradientShouldNudgeUserToConservativeStrategyInAdjustmentSegment() {
        VolunteerItem item = newItem();
        item.setGradient("冲");
        item.setChanceScore(35);
        item.setMatchScore(55);
        item.setDataSourceType("专业级");
        item.setPlanTrend("基本稳定");
        item.setLatestPlanCount(12);

        String reason = VolunteerService.buildRecommendReason(item, balancedProfile());

        assertThat(reason).contains("冲刺位");
        assertThat(reason).contains("可调节项：");
        assertThat(reason).contains("保守型");
    }

    @Test
    void riskSignalSegmentShouldHighlightShrinkingPlanAndLowPrecision() {
        VolunteerItem item = newItem();
        item.setGradient("保");
        item.setChanceScore(85);
        item.setDataSourceType("院校级");
        item.setPlanTrend("缩招");
        item.setLatestPlanCount(2);
        item.setSchoolEnrollmentIndex(30);
        item.setPrecisionScore(40);

        String reason = VolunteerService.buildRecommendReason(item, balancedProfile());

        String riskSegment = reason.split(" \\| ")[2];
        assertThat(riskSegment).startsWith("风险信号：");
        assertThat(riskSegment).contains("院校级回退");
        assertThat(riskSegment).contains("缩招");
        assertThat(riskSegment).contains("位次波动更敏感");
        assertThat(riskSegment).contains("供给指数偏低");
        assertThat(riskSegment).contains("推荐精度偏低");
    }

    @Test
    void sinoForeignItemShouldOfferToggleHintWhenUserExcludesIt() {
        VolunteerItem item = newItem();
        item.setUniversityName("某某大学（中外合作）");
        item.setGradient("稳");
        item.setChanceScore(60);
        item.setMatchScore(40);

        PreferenceProfile profile = balancedProfile();
        profile.setAcceptSinoForeign(false);

        String reason = VolunteerService.buildRecommendReason(item, profile);

        assertThat(reason).contains("可调节项：");
        assertThat(reason).contains("接受中外合作");
    }

    private static VolunteerItem newItem() {
        VolunteerItem item = new VolunteerItem();
        item.setUniversityName("贵州大学");
        item.setMajorName("计算机科学与技术");
        item.setProvince("贵州");
        item.setCity("贵阳");
        item.setTags(List.of("211", "双一流"));
        return item;
    }

    private static PreferenceProfile balancedProfile() {
        PreferenceProfile profile = new PreferenceProfile();
        profile.setStrategyMode("均衡型");
        profile.setDecisionPriority("专业优先");
        profile.setCareerGoal("就业优先");
        profile.setTuitionBudget("均衡预算");
        profile.setAcceptPrivate(true);
        profile.setAcceptSinoForeign(false);
        return profile;
    }
}
