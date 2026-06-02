package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.entity.UniOfficialLink;
import com.gzly.service.VolunteerService.HistoryRecord;
import com.gzly.service.VolunteerService.AdvisorAdvice;
import com.gzly.service.VolunteerService.ManualReviewItem;
import com.gzly.service.VolunteerService.PlanMetrics;
import com.gzly.service.VolunteerService.PlanResult;
import com.gzly.service.VolunteerService.VolunteerItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 VolunteerService 的复核清单、证据链与监控指标构建逻辑。
 *
 * 这些方法是 {@code private}，但都是纯函数（不依赖 Spring 上下文），
 * 因此通过反射直接调用，避免引入 Spring Boot 整体测试的开销。
 */
class VolunteerServiceManualReviewTest {

    private VolunteerService service;

    @BeforeEach
    void setUp() {
        // 仅注入测试用到的依赖，其余传 null。enrichWithEvidence 会用到 OfficialLinkService。
        OfficialLinkService officialLinkService = new OfficialLinkService(null) {
            @Override
            public Map<String, UniOfficialLink> loadBySchoolIds(Collection<String> schoolIds) {
                if (schoolIds == null || schoolIds.isEmpty()) return Collections.emptyMap();
                UniOfficialLink link = new UniOfficialLink();
                link.setSchoolId(schoolIds.iterator().next());
                link.setSchoolSite("https://example.edu.cn");
                link.setAdmissionSite("https://zsb.example.edu.cn");
                link.setAdmissionBrochureUrl("https://zsb.example.edu.cn/brochure");
                link.setMajorCatalogUrl("https://zsb.example.edu.cn/catalog");
                link.setTuitionInfoUrl("https://zsb.example.edu.cn/tuition");
                return Map.of(link.getSchoolId(), link);
            }
        };
        VolunteerMetricsRecorder recorder = new VolunteerMetricsRecorder();
        service = new VolunteerService(
                null, null, null, null, null,
                officialLinkService, new ObjectMapper(), null, recorder, new SafetyCodeService(), new ProvincePolicyService(), null,
                null, null, null, null, null);
    }

    @Test
    void enrichWithEvidence_marksItemsLackingOfficialRequirementForReview() throws Exception {
        VolunteerItem item = new VolunteerItem();
        item.setIndex(1);
        item.setSchoolId("1001");
        item.setUniversityName("贵州示例大学");
        item.setMajorName("临床医学");
        item.setGradient("稳");
        item.setDataSourceType("专业级");
        item.setSubjectRequirementSource("missing");
        item.setReferenceYear(2024);

        Method enrich = VolunteerService.class.getDeclaredMethod("enrichWithEvidence", List.class);
        enrich.setAccessible(true);
        enrich.invoke(service, List.of(item));

        assertThat(item.getAdmissionBrochureUrl()).isEqualTo("https://zsb.example.edu.cn/brochure");
        assertThat(item.getReviewFlags())
                .contains("missing_subject_requirement")
                .contains("medical_special");
        assertThat(item.isNeedsManualReview()).isTrue();
    }

    @Test
    void buildManualReviewList_collectsReasonsAndEvidenceLinks() throws Exception {
        VolunteerItem item = new VolunteerItem();
        item.setIndex(7);
        item.setSchoolId("1002");
        item.setUniversityName("贵州示例大学");
        item.setMajorName("国际经济与贸易（中外合作办学）");
        item.setGradient("冲");
        item.setDataSourceType("院校级");
        item.setSubjectRequirementSource("missing");
        item.setReferenceYear(2024);

        Method enrich = VolunteerService.class.getDeclaredMethod("enrichWithEvidence", List.class);
        enrich.setAccessible(true);
        enrich.invoke(service, List.of(item));

        Method build = VolunteerService.class.getDeclaredMethod("buildManualReviewList", List.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ManualReviewItem> result = (List<ManualReviewItem>) build.invoke(service, List.of(item));

        assertThat(result).hasSize(1);
        ManualReviewItem entry = result.get(0);
        assertThat(entry.getIndex()).isEqualTo(7);
        assertThat(entry.getReasons())
                .anyMatch(r -> r.contains("中外") || r.contains("缺少官方"))
                .anyMatch(r -> r.contains("院校级"));
        assertThat(entry.getEvidenceLinks()).contains("https://zsb.example.edu.cn/brochure");
    }

    @Test
    void buildPlanMetrics_aggregatesGradientAndReviewCounts() throws Exception {
        VolunteerItem chong = new VolunteerItem();
        chong.setGradient("冲");
        chong.setDataSourceType("专业级");
        chong.setSubjectRequirementSource("official_requirement");
        chong.setDataConfidenceScore(86);
        VolunteerItem dian = new VolunteerItem();
        dian.setGradient("垫");
        dian.setDataSourceType("院校级");
        dian.setSubjectRequirementSource("missing");
        dian.setLegacySubjectFallback(true);
        dian.setDataConfidenceScore(58);
        VolunteerItem wen = new VolunteerItem();
        wen.setGradient("稳");
        wen.setDataSourceType("专业级");
        wen.setSubjectRequirementSource("inferred");
        wen.setDataConfidenceScore(45);

        Method build = VolunteerService.class.getDeclaredMethod("buildPlanMetrics", List.class, List.class);
        build.setAccessible(true);
        ManualReviewItem stub = new ManualReviewItem();
        stub.setIndex(1);
        PlanMetrics metrics = (PlanMetrics) build.invoke(service, List.of(chong, dian, wen), List.of(stub));

        assertThat(metrics.getTotalCount()).isEqualTo(3);
        assertThat(metrics.getChongCount()).isEqualTo(1);
        assertThat(metrics.getWenCount()).isEqualTo(1);
        assertThat(metrics.getDianCount()).isEqualTo(1);
        assertThat(metrics.getMissingRequirementCount()).isEqualTo(2);
        assertThat(metrics.getNonMajorLevelCount()).isEqualTo(1);
        assertThat(metrics.getManualReviewCount()).isEqualTo(1);
        assertThat(metrics.getOfficialRequirementCount()).isEqualTo(1);
        assertThat(metrics.getLegacyFallbackCount()).isEqualTo(1);
        assertThat(metrics.getLowConfidenceCount()).isEqualTo(2);
        assertThat(metrics.getSpecialExcludedCount()).isEqualTo(0);
        assertThat(metrics.getMissingPlanIndexCount()).isEqualTo(3);
        assertThat(metrics.getAvgPrecisionScore()).isEqualTo(0);
    }

    @Test
    void assessPortfolioSafety_usesSafeTailItemsForListLevelRisk() throws Exception {
        VolunteerItem bao = safeTailItem("保", 82);
        VolunteerItem dian = safeTailItem("垫", 90);
        VolunteerItem chong = safeTailItem("冲", 35);

        Method assess = VolunteerService.class.getDeclaredMethod("assessPortfolioSafety", List.class);
        assess.setAccessible(true);
        Object safety = assess.invoke(service, List.of(bao, dian, chong));
        Method probability = safety.getClass().getDeclaredMethod("probability");
        Method safeTailCount = safety.getClass().getDeclaredMethod("safeTailCount");
        probability.setAccessible(true);
        safeTailCount.setAccessible(true);

        assertThat((double) probability.invoke(safety)).isGreaterThan(90);
        assertThat((int) safeTailCount.invoke(safety)).isEqualTo(2);
    }

    @Test
    void planAndRankSignals_feedAlgorithmExplanationAndConfidence() throws Exception {
        VolunteerItem item = new VolunteerItem();
        item.setGradient("稳");
        item.setHistoryMinRank(36_000);
        item.setReferenceYear(2025);
        item.setDataSourceType("专业级");
        item.setSubjectRequirementSource("official_requirement");
        item.setConfidenceLabel("高可信");
        item.setAdmissionProb(62);
        item.setProbLevel("参考中等");
        item.setWithinConfiguredRange(true);
        item.setHistoryRecords(List.of(
                historyRecord(2025, 18),
                historyRecord(2024, 12)));

        Method enrichPlan = VolunteerService.class.getDeclaredMethod("enrichWithPlanAndRankSignals", List.class, int.class);
        enrichPlan.setAccessible(true);
        enrichPlan.invoke(service, List.of(item), 32_000);

        Method explain = VolunteerService.class.getDeclaredMethod("applyReliabilityExplanations", List.class, int.class);
        explain.setAccessible(true);
        explain.invoke(service, List.of(item), 32_000);

        assertThat(item.getRankGap()).isEqualTo(4_000);
        assertThat(item.getRankGapRatio()).isEqualTo(12.5);
        assertThat(item.getLatestPlanCount()).isEqualTo(18);
        assertThat(item.getPlanTrend()).isEqualTo("扩招");
        assertThat(item.getPlanExpansionIndex()).isEqualTo(150.0);
        assertThat(item.getPlanExpansionLabel()).isEqualTo("实际扩招");
        assertThat(item.getSchoolEnrollmentIndex()).isGreaterThan(60);
        assertThat(item.getPrecisionScore()).isGreaterThan(70);
        assertThat(item.getPrecisionLabel()).isIn("精度较高", "精度中等");
        assertThat(item.getDataConfidenceScore()).isGreaterThan(90);
        assertThat(item.getAlgorithmExplanation())
                .contains("计划趋势：扩招")
                .contains("位次差约12.5%")
                .contains("当年实际扩招指数150.0")
                .contains("院校招生供给指数");
    }

    @Test
    void advisorAdvice_summarizesFeasibleSetPlanRiskAndActions() throws Exception {
        PlanResult plan = new PlanResult();
        plan.setProvinceName("贵州");
        plan.setProvinceRank(32_000);
        plan.setTargetCount(96);
        plan.setDecisionPriority("专业优先");
        plan.setCareerGoal("就业优先");
        plan.setPreferredMajors(List.of("计算机"));
        plan.setPreferredRegions(List.of("成都"));
        PlanMetrics metrics = new PlanMetrics();
        metrics.setManualReviewCount(2);
        plan.setMetrics(metrics);

        VolunteerItem stable = new VolunteerItem();
        stable.setGradient("稳");
        stable.setDataSourceType("专业级");
        stable.setUniversityName("成都示例大学");
        stable.setMajorName("计算机科学与技术");
        stable.setProvince("四川");
        stable.setCity("成都");
        stable.setPlanTrend("扩招");
        stable.setLatestPlanCount(18);
        VolunteerItem risky = new VolunteerItem();
        risky.setGradient("保");
        risky.setDataSourceType("专业级");
        risky.setMajorName("软件工程");
        risky.setPlanTrend("缩招");
        risky.setLatestPlanCount(2);
        risky.setRiskColor("red");
        plan.setItems(List.of(stable, risky));

        Method build = VolunteerService.class.getDeclaredMethod("buildAdvisorAdvice", PlanResult.class);
        build.setAccessible(true);
        AdvisorAdvice advice = (AdvisorAdvice) build.invoke(service, plan);

        assertThat(advice.getPositioning()).contains("第32,000名").contains("可行集");
        assertThat(advice.getPlanChangeAdvice()).contains("扩招1项").contains("缩招1项");
        assertThat(advice.getCityAdvice()).contains("成都").contains("命中1个");
        assertThat(advice.getRiskChecklist()).anyMatch(risk -> risk.contains("缩招"));
        assertThat(advice.getActionItems()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(advice.getTitle()).contains("张雪峰.skill");
        assertThat(advice.getSourceProjectName()).isEqualTo("Eric-Yibo-Shen/zhangxuefeng-skillset");
        assertThat(advice.getSourceProjectUrl()).isEqualTo("https://github.com/Eric-Yibo-Shen/zhangxuefeng-skillset");
        assertThat(advice.getSourceNote())
                .contains("Eric-Yibo-Shen/zhangxuefeng-skillset")
                .contains("alchaincyf/zhangxuefeng-skill")
                .contains("不构成录取承诺");
    }

    private HistoryRecord historyRecord(int year, int planCount) {
        HistoryRecord record = new HistoryRecord();
        record.setYear(year);
        record.setMinScore(520);
        record.setMinRank(year == 2025 ? 36_000 : 37_500);
        record.setPlanCount(planCount);
        record.setDataSourceType("专业级");
        return record;
    }

    private VolunteerItem safeTailItem(String gradient, double probability) {
        VolunteerItem item = new VolunteerItem();
        item.setGradient(gradient);
        item.setAdmissionProb(probability);
        item.setDataConfidenceScore(82);
        item.setPrecisionScore(78);
        item.setConfidenceLabel("高可信");
        item.setRiskColor("green");
        item.setPlanTrend("基本稳定");
        return item;
    }
}
