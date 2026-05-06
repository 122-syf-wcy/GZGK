package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.UniOfficialLink;
import com.gzly.entity.University;
import com.gzly.mapper.AlumniAdminMapper;
import com.gzly.mapper.AnnouncementMapper;
import com.gzly.mapper.BizUserFeedbackMapper;
import com.gzly.mapper.BizUserMapper;
import com.gzly.mapper.EncouragementMessageMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import com.gzly.mapper.SpecialAdmissionPolicyMapper;
import com.gzly.mapper.UniOfficialLinkMapper;
import com.gzly.mapper.UniversityMapper;
import com.gzly.service.AiConfigService;
import com.gzly.service.OfficialLinkPriorityService;
import com.gzly.service.UniversityQaService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ExtendWith(MockitoExtension.class)
class AdminControllerOfficialLinksTest {

    @Mock private BizUserMapper userMapper;
    @Mock private PlanHistoryMapper planMapper;
    @Mock private ScoreLineGzMapper scoreLineMapper;
    @Mock private UniversityMapper universityMapper;
    @Mock private AlumniAdminMapper alumniMapper;
    @Mock private MajorScoreGzMapper majorScoreMapper;
    @Mock private UniOfficialLinkMapper officialLinkMapper;
    @Mock private SpecialAdmissionPolicyMapper specialAdmissionPolicyMapper;
    @Mock private AnnouncementMapper announcementMapper;
    @Mock private BizUserFeedbackMapper feedbackMapper;
    @Mock private EncouragementMessageMapper encouragementMessageMapper;
    @Mock private UniversityQaService qaService;
    @Mock private AiConfigService aiConfigService;
    @Mock private OfficialLinkPriorityService officialLinkPriorityService;
    @Mock private VolunteerMetricsRecorder volunteerMetricsRecorder;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(
                userMapper,
                planMapper,
                scoreLineMapper,
                universityMapper,
                alumniMapper,
                majorScoreMapper,
                officialLinkMapper,
                specialAdmissionPolicyMapper,
                announcementMapper,
                feedbackMapper,
                encouragementMessageMapper,
                qaService,
                aiConfigService,
                officialLinkPriorityService,
                volunteerMetricsRecorder,
                jwtUtil
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnPriorityQueueSortedByPriorityAndHotness() throws Exception {
        University schoolA = university(1L, "100", "贵州大学");
        University schoolB = university(2L, "200", "贵州师范大学");
        University schoolC = university(3L, "300", "遵义医科大学");

        UniOfficialLink linkA = officialLink("100", "", "", "https://a-brochure");
        UniOfficialLink linkB = officialLink("200", "https://b-tuition", "已有收费摘要", "https://b-brochure");
        linkB.setMajorCatalogUrl("https://b-major");
        linkB.setParseStatus(1);
        UniOfficialLink linkC = officialLink("300", "", "", "");

        when(universityMapper.selectList(any())).thenReturn(List.of(schoolA, schoolB, schoolC));
        when(universityMapper.selectCount(any())).thenReturn(3L);
        when(officialLinkMapper.selectList(any())).thenReturn(List.of(linkA, linkB, linkC));
        when(officialLinkMapper.selectCount(any())).thenReturn(0L);

        OfficialLinkPriorityService.PriorityContext context =
                new OfficialLinkPriorityService.PriorityContext(30, false, 128, Map.of("100", 3, "300", 5), 5);
        when(officialLinkPriorityService.buildPriorityContext(any(), eq(30))).thenReturn(context);
        when(officialLinkPriorityService.buildPriorityMeta(eq("100"), any(), eq(context)))
                .thenReturn(new OfficialLinkPriorityService.PriorityMeta(
                        3, 60, 2, List.of("tuitionInfoUrl", "tuitionSummary"), "P1",
                        List.of("近30天方案命中 3 次", "缺收费标准链接", "缺收费摘要")
                ));
        when(officialLinkPriorityService.buildPriorityMeta(eq("200"), any(), eq(context)))
                .thenReturn(new OfficialLinkPriorityService.PriorityMeta(
                        0, 0, 0, List.of(), "P2", List.of()
                ));
        when(officialLinkPriorityService.buildPriorityMeta(eq("300"), any(), eq(context)))
                .thenReturn(new OfficialLinkPriorityService.PriorityMeta(
                        5, 100, 4, List.of("tuitionInfoUrl", "tuitionSummary", "admissionBrochureUrl", "parsedContent"), "P0",
                        List.of("近30天方案命中 5 次", "缺收费标准链接")
                ));

        mockMvc.perform(get("/admin/official-links")
                        .param("priorityOnly", "true")
                        .param("sortBy", "priority")
                        .param("missingField", "tuitionInfoUrl")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].schoolId").value("300"))
                .andExpect(jsonPath("$.data.items[0].priorityLevel").value("P0"))
                .andExpect(jsonPath("$.data.items[0].planHitCount").value(5))
                .andExpect(jsonPath("$.data.items[1].schoolId").value("100"))
                .andExpect(jsonPath("$.data.items[1].priorityLevel").value("P1"))
                .andExpect(jsonPath("$.data.items[1].planHitCount").value(3))
                .andExpect(jsonPath("$.data.stats.activeWindowDays").value(30))
                .andExpect(jsonPath("$.data.stats.recentPlanCount").value(128))
                .andExpect(jsonPath("$.data.stats.priorityQueueCount").value(2));
    }

    @Test
    void shouldPreserveExistingNonEmptyFieldsWhenPreserveModeEnabled() throws Exception {
        UniOfficialLink existing = officialLink("514", "https://old-tuition", "已有收费摘要", "https://old-brochure");
        existing.setId(9L);
        existing.setSchoolSite("https://old-school");
        existing.setAdmissionSite("");
        existing.setParserNotes("旧备注");
        existing.setCaptureStatus(1);
        existing.setParseStatus(1);

        when(officialLinkMapper.selectOne(any())).thenReturn(existing);

        mockMvc.perform(post("/admin/official-links")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"514",
                                  "schoolName":"贵州师范大学",
                                  "schoolSite":"https://new-school",
                                  "admissionSite":"https://new-admission",
                                  "admissionBrochureUrl":"",
                                  "tuitionSummary":"新的收费摘要",
                                  "parserNotes":"人工补充备注",
                                  "preserveNonEmpty":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schoolSite").value("https://old-school"))
                .andExpect(jsonPath("$.data.admissionSite").value("https://new-admission"))
                .andExpect(jsonPath("$.data.admissionBrochureUrl").value("https://old-brochure"))
                .andExpect(jsonPath("$.data.tuitionSummary").value("已有收费摘要"))
                .andExpect(jsonPath("$.data.parserNotes").value("旧备注\n\n人工补充备注"));
    }

    @Test
    void shouldTreatSuspiciousNonEmptyUrlsAsMissingAndExcludeFromUsableCounts() throws Exception {
        University schoolA = university(1L, "100", "可疑链接学校");
        University schoolB = university(2L, "200", "正常链接学校");

        UniOfficialLink suspicious = officialLink("100", "https://weibo.com/a-school", "", "https://a.edu.cn/");
        suspicious.setSchoolSite("https://a.edu.cn/");
        suspicious.setAdmissionSite("#");
        suspicious.setMajorCatalogUrl("javascript:void(0)");
        suspicious.setCaptureStatus(1);
        suspicious.setParseStatus(0);

        UniOfficialLink valid = officialLink("200", "https://zsb.b.edu.cn/fee.html", "已补收费摘要", "https://zsb.b.edu.cn/brochure.html");
        valid.setSchoolSite("https://b.edu.cn/");
        valid.setAdmissionSite("https://zsb.b.edu.cn/");
        valid.setMajorCatalogUrl("https://zsb.b.edu.cn/major.html");
        valid.setCaptureStatus(1);
        valid.setParseStatus(1);

        when(universityMapper.selectList(any())).thenReturn(List.of(schoolA, schoolB));
        when(universityMapper.selectCount(any())).thenReturn(2L);
        when(officialLinkMapper.selectList(any()))
                .thenReturn(List.of(suspicious, valid))
                .thenReturn(List.of(suspicious, valid));
        when(officialLinkMapper.selectCount(any())).thenReturn(0L);

        OfficialLinkPriorityService.PriorityContext context =
                new OfficialLinkPriorityService.PriorityContext(90, false, 46, Map.of("100", 9, "200", 5), 9);
        when(officialLinkPriorityService.buildPriorityContext(any(), eq(30))).thenReturn(context);
        when(officialLinkPriorityService.buildPriorityMeta(eq("100"), any(), eq(context)))
                .thenReturn(new OfficialLinkPriorityService.PriorityMeta(
                        9, 100, 4, List.of("tuitionInfoUrl", "admissionBrochureUrl", "majorCatalogUrl", "parsedContent"), "P0",
                        List.of("近90天方案命中 9 次", "缺收费标准链接")
                ));
        when(officialLinkPriorityService.buildPriorityMeta(eq("200"), any(), eq(context)))
                .thenReturn(new OfficialLinkPriorityService.PriorityMeta(
                        5, 56, 0, List.of(), "P2", List.of()
                ));

        mockMvc.perform(get("/admin/official-links")
                        .param("priorityOnly", "false")
                        .param("missingField", "tuitionInfoUrl")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].schoolId").value("100"))
                .andExpect(jsonPath("$.data.stats.brochureCount").value(1))
                .andExpect(jsonPath("$.data.stats.majorCatalogCount").value(1))
                .andExpect(jsonPath("$.data.stats.tuitionCount").value(1));
    }

    private University university(Long id, String schoolId, String name) {
        University university = new University();
        university.setId(id);
        university.setSchoolId(schoolId);
        university.setName(name);
        university.setProvince("贵州");
        university.setCity("贵阳");
        university.setNatureName("公办");
        university.setTags(List.of("公办"));
        return university;
    }

    private UniOfficialLink officialLink(String schoolId, String tuitionInfoUrl, String tuitionSummary, String brochureUrl) {
        UniOfficialLink link = new UniOfficialLink();
        link.setSchoolId(schoolId);
        link.setTuitionInfoUrl(tuitionInfoUrl);
        link.setTuitionSummary(tuitionSummary);
        link.setAdmissionBrochureUrl(brochureUrl);
        link.setCaptureStatus(1);
        link.setParseStatus(0);
        return link;
    }
}
