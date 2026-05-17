package com.gzly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.PlanHistory;
import com.gzly.service.AiService;
import com.gzly.service.AlgorithmService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProvinceRankService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.service.VolunteerService;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VolunteerControllerAccessSecurityTest {

    @Mock private VolunteerService volunteerService;
    @Mock private ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    @Mock private AiService aiService;
    @Mock private AlgorithmService algorithmService;
    @Mock private ProvincePolicyService provincePolicyService;
    @Mock private ProvinceRankService provinceRankService;
    @Mock private VolunteerMetricsRecorder metricsRecorder;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        VolunteerController controller = new VolunteerController(
                volunteerService,
                professionalGroupVolunteerService,
                aiService,
                algorithmService,
                provincePolicyService,
                provinceRankService,
                metricsRecorder,
                jwtUtil,
                objectMapper,
                directExecutor,
                stringRedisTemplate
        );
        ReflectionTestUtils.setField(controller, "aiAnalysisActiveGlobalLimit", 30);
        ReflectionTestUtils.setField(controller, "aiAnalysisActivePerIpLimit", 2);
        ReflectionTestUtils.setField(controller, "aiAnalysisActiveTtlSeconds", 180);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldFetchVolunteerPlanByPostBody() throws Exception {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(99L);
        plan.setAccessKey("secret-access-key");

        when(volunteerService.getPlanResult(99L, "secret-access-key")).thenReturn(plan);

        mockMvc.perform(post("/volunteer/plan")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planId\":99,\"accessKey\":\"secret-access-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.accessKey").value("secret-access-key"));
    }

    @Test
    void rankCheck_missingRequiredScore_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/volunteer/rank-check")
                        .param("provinceCode", "SC")
                        .param("firstSubject", "物理"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必要参数：totalScore"));
    }

    @Test
    void shouldCreateShortLivedAiAnalysisTicket() throws Exception {
        PlanHistory plan = new PlanHistory();
        plan.setId(99L);

        when(volunteerService.getPlanById(99L)).thenReturn(plan);
        when(volunteerService.isValidPlanAccessKey(99L, "secret-access-key")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        mockMvc.perform(post("/volunteer/ai-analysis-ticket")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planId\":99,\"accessKey\":\"secret-access-key\",\"profile\":\"稳妥优先\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(120));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), payloadCaptor.capture(), eq(Duration.ofSeconds(120)));
        assertThat(keyCaptor.getValue()).startsWith("volunteer:ai-ticket:");
        assertThat(payloadCaptor.getValue()).contains("\"planId\":99");
        assertThat(payloadCaptor.getValue()).contains("\"safetyCode\":\"secret-access-key\"");
        assertThat(payloadCaptor.getValue()).contains("\"profile\":\"稳妥优先\"");
    }

    @Test
    void shouldRejectAiAnalysisTicketWhenAccessKeyInvalid() throws Exception {
        when(volunteerService.getPlanById(99L)).thenReturn(new PlanHistory());
        when(volunteerService.isValidPlanAccessKey(99L, "bad-key")).thenReturn(false);

        mockMvc.perform(post("/volunteer/ai-analysis-ticket")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planId\":99,\"accessKey\":\"bad-key\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("安全码错误或无权访问该方案"));
    }

    @Test
    void shouldRejectAiAnalysisTicketWhenSafetyCodeMissing() throws Exception {
        mockMvc.perform(post("/volunteer/ai-analysis-ticket")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planId\":99}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("安全码错误或无权访问该方案"));
    }

    @Test
    void shouldRejectZxfSkillsChatWhenSafetyCodeMissing() throws Exception {
        mockMvc.perform(post("/volunteer/zhangxuefeng-skills-chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"planId\":99,\"message\":\"哪些志愿该删\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("安全码错误或无权访问该方案"));
    }

    @Test
    void shouldChatWithZxfSkillsAfterPlanAccessValidated() throws Exception {
        PlanHistory plan = new PlanHistory();
        plan.setId(99L);
        plan.setTotalScore(601);
        plan.setProvinceRank(18970);
        plan.setFirstSubject("物理");
        plan.setResubjects("[\"化学\",\"生物\"]");
        plan.setPlanJson("[]");
        plan.setManualReviewJson("[]");
        plan.setMetricsJson("{}");
        plan.setRequestSnapshotJson("{\"rankEstimate\":{\"rankEstimated\":false}}");

        when(volunteerService.getPlanById(99L)).thenReturn(plan);
        when(volunteerService.isValidPlanAccessKey(99L, "secret-access-key")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.decrement(anyString())).thenReturn(0L);
        when(aiService.chatWithAdvisorSkill(anyString(), eq("AI报告"), eq("哪些志愿该删"), any()))
                .thenReturn("> 本内容由 AI 生成，仅供参考。\n\n建议先删掉数据置信度最低的冲档项。");

        mockMvc.perform(post("/volunteer/zhangxuefeng-skills-chat")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"planId":99,"accessKey":"secret-access-key","message":"哪些志愿该删","aiReport":"AI报告"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reply").value("> 本内容由 AI 生成，仅供参考。\n\n建议先删掉数据置信度最低的冲档项。"))
                .andExpect(jsonPath("$.data.sourceProjectName").value(VolunteerService.ADVISOR_SOURCE_PROJECT_NAME));
    }

    @Test
    void aiAnalysis_shouldReleasePlanLockByOwnerToken() throws Exception {
        PlanHistory plan = new PlanHistory();
        plan.setId(99L);
        plan.setTotalScore(601);
        plan.setProvinceRank(18970);
        plan.setFirstSubject("物理");
        plan.setResubjects("[\"化学\",\"生物\"]");
        plan.setPlanJson("[]");
        plan.setManualReviewJson("[]");
        plan.setMetricsJson("{}");
        plan.setRequestSnapshotJson("{}");
        AtomicReference<String> planLockToken = new AtomicReference<>();

        when(volunteerService.getPlanById(99L)).thenReturn(plan);
        when(volunteerService.isValidPlanAccessKey(99L, "secret-access-key")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("active:ai-analysis:plan:99"), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    planLockToken.set(inv.getArgument(1, String.class));
                    return Boolean.TRUE;
                });
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.decrement(anyString())).thenReturn(0L);
        doAnswer(inv -> {
            inv.getArgument(0, SseEmitter.class).complete();
            return null;
        }).when(aiService).streamAnalysis(any(SseEmitter.class), anyString());

        mockMvc.perform(get("/volunteer/ai-analysis")
                        .param("planId", "99")
                        .param("accessKey", "secret-access-key"))
                .andExpect(status().isOk());

        assertThat(planLockToken.get()).isNotBlank();
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("active:ai-analysis:plan:99")),
                eq(planLockToken.get()));
    }
}
