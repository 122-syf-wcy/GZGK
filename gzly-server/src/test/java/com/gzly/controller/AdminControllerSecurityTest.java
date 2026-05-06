package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.mapper.*;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerSecurityTest {

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
        ReflectionTestUtils.setField(controller, "adminPasswordHash",
                new BCryptPasswordEncoder().encode("correct-password"));
        ReflectionTestUtils.setField(controller, "adminLoginLockLimit", 2);
        ReflectionTestUtils.setField(controller, "adminLoginWindowSeconds", 300);
        ReflectionTestUtils.setField(controller, "adminLoginLockSeconds", 900);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldLoginWithBcryptPasswordHash() throws Exception {
        when(jwtUtil.generateAdmin(0L, "admin")).thenReturn("admin-token");

        mockMvc.perform(post("/admin/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("admin-token"));
    }

    @Test
    void shouldLockAdminLoginAfterRepeatedFailures() throws Exception {
        mockMvc.perform(post("/admin/login")
                .contentType(APPLICATION_JSON)
                .content("{\"password\":\"bad-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));

        mockMvc.perform(post("/admin/login")
                .contentType(APPLICATION_JSON)
                .content("{\"password\":\"bad-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));

        mockMvc.perform(post("/admin/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请稍后再试"));
    }
}
