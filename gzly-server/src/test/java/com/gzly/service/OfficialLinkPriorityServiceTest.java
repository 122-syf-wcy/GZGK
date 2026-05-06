package com.gzly.service;

import com.gzly.GzlyApplication;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.UniOfficialLink;
import com.gzly.mapper.PlanHistoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = GzlyApplication.class, properties = {
        "gzly.jwt.secret=test-secret-for-gzly-jwt-at-least-32-characters",
        "gzly.admin.password=test-admin-password"
})
class OfficialLinkPriorityServiceTest {

    @MockBean
    private PlanHistoryMapper planHistoryMapper;

    @Autowired
    private OfficialLinkPriorityService officialLinkPriorityService;

    @Test
    void shouldCountDistinctSchoolHitPerPlan() {
        when(planHistoryMapper.selectList(any())).thenReturn(List.of(
                plan(1L, "[{\"schoolId\":\"100\"},{\"schoolId\":\"100\"},{\"schoolId\":\"200\"}]"),
                plan(2L, "[{\"schoolId\":\"100\"},{\"schoolId\":\"300\"}]"),
                plan(3L, "not-json")
        ));

        OfficialLinkPriorityService.PriorityContext context =
                officialLinkPriorityService.buildPriorityContext(List.of("100", "200", "300"), 90);

        assertThat(context.activeWindowDays()).isEqualTo(90);
        assertThat(context.fallbackTriggered()).isFalse();
        assertThat(context.recentPlanCount()).isEqualTo(3);
        assertThat(context.planHitCountMap())
                .containsEntry("100", 2)
                .containsEntry("200", 1)
                .containsEntry("300", 1);
        assertThat(context.maxPlanHitCount()).isEqualTo(2);
    }

    @Test
    void shouldFallbackToNinetyDaysWhenThirtyDaySampleIsTooSmall() {
        List<PlanHistory> shortWindowPlans = List.of(
                plan(10L, "[{\"schoolId\":\"100\"}]"),
                plan(11L, "[{\"schoolId\":\"200\"}]")
        );
        List<PlanHistory> ninetyDayPlans = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(i -> plan((long) i, "[{\"schoolId\":\"100\"}]"))
                .toList();

        when(planHistoryMapper.selectList(any())).thenReturn(shortWindowPlans, ninetyDayPlans);

        OfficialLinkPriorityService.PriorityContext context =
                officialLinkPriorityService.buildPriorityContext(List.of("100", "200"), 30);

        assertThat(context.activeWindowDays()).isEqualTo(90);
        assertThat(context.fallbackTriggered()).isTrue();
        assertThat(context.recentPlanCount()).isEqualTo(100);
        assertThat(context.planHitCountMap()).containsEntry("100", 100);
        assertThat(context.maxPlanHitCount()).isEqualTo(100);
    }

    @Test
    void shouldTreatBlockedAndPlaceholderDetailLinksAsMissing() {
        UniOfficialLink link = new UniOfficialLink();
        link.setSchoolId("107");
        link.setSchoolSite("https://www.nwpu.edu.cn/");
        link.setAdmissionSite("https://zsb.nwpu.edu.cn/");
        link.setTuitionInfoUrl("https://cctvnews.cctv.com/2026/04/11/ARTIxxxx.shtml");
        link.setTuitionSummary("已补收费摘要");
        link.setAdmissionBrochureUrl("https://www.nwpu.edu.cn/#");
        link.setMajorCatalogUrl("javascript:void(0)");
        link.setParseStatus(1);

        OfficialLinkPriorityService.PriorityMeta meta = officialLinkPriorityService.buildPriorityMeta(
                "107",
                link,
                new OfficialLinkPriorityService.PriorityContext(30, false, 0, Map.of(), 0)
        );

        assertThat(meta.missingFields()).containsExactlyInAnyOrder(
                "tuitionInfoUrl",
                "admissionBrochureUrl",
                "majorCatalogUrl"
        );
        assertThat(meta.gapCount()).isEqualTo(3);
    }

    @Test
    void shouldTreatStationEntranceLinksAsMissingForDetailFields() {
        UniOfficialLink link = new UniOfficialLink();
        link.setSchoolId("1570");
        link.setSchoolSite("https://www.gznc.edu.cn/");
        link.setAdmissionSite("https://zhaojiu.gznc.edu.cn/");
        link.setTuitionInfoUrl("https://www.gznc.edu.cn/xxgk/xxzc.htm");
        link.setTuitionSummary("3830/4100/4200/9000 元/生·年");
        link.setAdmissionBrochureUrl("https://zhaojiu.gznc.edu.cn/");
        link.setMajorCatalogUrl("https://www.gznc.edu.cn/");
        link.setParseStatus(1);

        OfficialLinkPriorityService.PriorityMeta meta = officialLinkPriorityService.buildPriorityMeta(
                "1570",
                link,
                new OfficialLinkPriorityService.PriorityContext(30, false, 0, Map.of(), 0)
        );

        assertThat(meta.missingFields()).containsExactlyInAnyOrder(
                "admissionBrochureUrl",
                "majorCatalogUrl"
        );
        assertThat(meta.missingFields()).doesNotContain("tuitionInfoUrl", "tuitionSummary", "parsedContent");
    }

    @Test
    void shouldKeepOfficialDetailLinksAsComplete() {
        UniOfficialLink link = new UniOfficialLink();
        link.setSchoolId("127");
        link.setSchoolSite("https://www.hust.edu.cn/");
        link.setAdmissionSite("https://zsb.hust.edu.cn/");
        link.setTuitionInfoUrl("https://zsb.hust.edu.cn/info/1217/2843.htm");
        link.setTuitionSummary("其他专业 4500-5850 元/学年不等");
        link.setAdmissionBrochureUrl("https://zsb.hust.edu.cn/info/1217/2843.htm");
        link.setMajorCatalogUrl("https://zsb.hust.edu.cn/lnfs.htm");
        link.setParseStatus(1);

        OfficialLinkPriorityService.PriorityMeta meta = officialLinkPriorityService.buildPriorityMeta(
                "127",
                link,
                new OfficialLinkPriorityService.PriorityContext(30, false, 0, Map.of(), 0)
        );

        assertThat(meta.missingFields()).isEmpty();
        assertThat(meta.gapCount()).isZero();
    }

    private PlanHistory plan(Long id, String planJson) {
        PlanHistory planHistory = new PlanHistory();
        planHistory.setId(id);
        planHistory.setPlanJson(planJson);
        planHistory.setCreatedAt(LocalDateTime.now());
        return planHistory;
    }
}
