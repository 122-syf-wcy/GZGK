package com.gzly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.service.AiDeepAnalysisService;
import com.gzly.service.AiService;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.SkillsRagService;
import com.gzly.service.VolunteerExportService;
import com.gzly.service.VolunteerMetricsRecorder;
import com.gzly.service.VolunteerService;
import com.gzly.mapper.SafetyCodeIdentityMapper;
import com.gzly.service.SafetyCodeIdentityService;
import com.gzly.service.recommend.QueryOnlyRecommendEngine;
import com.gzly.service.recommend.RecommendEngineDecision;
import com.gzly.service.recommend.RecommendEngineRouter;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SafetyCodePlanAccessControllerTest {

    @Test
    void recommend_withoutSafetyCode_shouldReject() throws Exception {
        FakeVolunteerService volunteerService = new FakeVolunteerService(planResult("SAFE1234"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(recommendController(volunteerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"GZ\",\"batchCode\":\"NORMAL_UNDERGRADUATE\",\"totalScore\":600,\"provinceRank\":20000,\"firstSubject\":\"物理\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("请先输入安全码"));

        assertThat(volunteerService.generateCalled).isFalse();
    }

    @Test
    void recommend_withSafetyCode_shouldGeneratePlanAndBindIt() throws Exception {
        FakeVolunteerService volunteerService = new FakeVolunteerService(planResult("SAFE1234"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(recommendController(volunteerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .header("X-Safety-Code", "SAFE1234")
                        .content("{\"provinceCode\":\"GZ\",\"batchCode\":\"NORMAL_UNDERGRADUATE\",\"totalScore\":600,\"provinceRank\":20000,\"firstSubject\":\"物理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.safetyCode").value("SAFE1234"));

        assertThat(volunteerService.generateCalled).isTrue();
        assertThat(volunteerService.lastUserId).isNull();
        assertThat(volunteerService.lastRequestSafetyCode).isEqualTo("SAFE1234");
    }

    @Test
    void recommend_shouldNotDependOnCardKeyService() {
        boolean hasCardKeyDependency = Arrays.stream(VolunteerRecommendController.class.getDeclaredFields())
                .anyMatch(field -> "CardKeyService".equals(field.getType().getSimpleName()));
        assertThat(hasCardKeyDependency).isFalse();
    }

    @Test
    void issue_shouldStoreHashOnlyAndVerifySafetyCode() {
        SafetyCodeService service = safetyCodeService(null);

        SafetyCodeService.SafetyCodeIssue issue = service.issue("SAFE1234");

        assertThat(issue.safetyCode()).isEqualTo("SAFE1234");
        assertThat(issue.safetyCodeHash()).startsWith("$2");
        assertThat(issue.safetyCodeHash()).doesNotContain("SAFE1234");
        assertThat(service.verify(99L, "SAFE1234", issue.safetyCodeHash())).isTrue();
    }

    @Test
    void getPlan_withHeaderSafetyCode_shouldPass() throws Exception {
        FakeSafetyCodeService safetyCodeService = new FakeSafetyCodeService(true);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerPlanController(new FakeVolunteerService(planResult("")), new SafetyCodeRequestResolver(), safetyCodeService, new SafetyCodeIdentityService(Mockito.mock(SafetyCodeIdentityMapper.class), safetyCodeService), Mockito.mock(com.gzly.mapper.PlanHistoryMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/volunteer/plans/99").header("X-Safety-Code", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));

        assertThat(safetyCodeService.lastCode).isEqualTo("SAFE1234");
    }

    @Test
    void getPlan_withWrongSafetyCode_shouldReturn403() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerPlanController(new FakeVolunteerService(planResult("")), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false), new SafetyCodeIdentityService(Mockito.mock(SafetyCodeIdentityMapper.class), new FakeSafetyCodeService(false)), Mockito.mock(com.gzly.mapper.PlanHistoryMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/volunteer/plans/99").param("safetyCode", "WRONG99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void verifySafetyCode_withCorrectSafetyCode_shouldReturnOnlyValidity() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerPlanController(new FakeVolunteerService(planResult("")), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true), new SafetyCodeIdentityService(Mockito.mock(SafetyCodeIdentityMapper.class), new FakeSafetyCodeService(true)), Mockito.mock(com.gzly.mapper.PlanHistoryMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/plans/99/verify-safety-code")
                        .contentType(APPLICATION_JSON)
                        .content("{\"safetyCode\":\"SAFE1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.planId").value(99))
                .andExpect(jsonPath("$.data.safetyCodeHash").doesNotExist())
                .andExpect(jsonPath("$.data.safetyCode").doesNotExist())
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    @Test
    void verifySafetyCode_withWrongSafetyCode_shouldReturn403() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerPlanController(new FakeVolunteerService(planResult("")), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false), new SafetyCodeIdentityService(Mockito.mock(SafetyCodeIdentityMapper.class), new FakeSafetyCodeService(false)), Mockito.mock(com.gzly.mapper.PlanHistoryMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/plans/99/verify-safety-code")
                        .contentType(APPLICATION_JSON)
                        .content("{\"safetyCode\":\"WRONG99\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("安全码错误或无权访问该方案"));
    }

    @Test
    void protectedPlanAiSkillsAndExportEndpoints_withoutSafetyCode_shouldReturn403() throws Exception {
        MockMvc planMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerPlanController(new FakeVolunteerService(planResult("")), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false), new SafetyCodeIdentityService(Mockito.mock(SafetyCodeIdentityMapper.class), new FakeSafetyCodeService(false)), Mockito.mock(com.gzly.mapper.PlanHistoryMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMvc aiMvc = MockMvcBuilders.standaloneSetup(
                        new AiAnalysisController(new FakeAiDeepAnalysisService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMvc skillsMvc = MockMvcBuilders.standaloneSetup(
                        new SkillsQaController(new FakeSkillsRagService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMvc exportMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerExportController(new FakeVolunteerExportService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        planMvc.perform(get("/volunteer/plans/99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        aiMvc.perform(get("/volunteer/plans/99/ai-analysis"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        aiMvc.perform(post("/volunteer/plans/99/ai-analysis").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        skillsMvc.perform(get("/volunteer/plans/99/skills/suggested-questions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        skillsMvc.perform(post("/volunteer/plans/99/skills/ask").contentType(APPLICATION_JSON).content("{\"question\":\"怎么调整\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        exportMvc.perform(post("/volunteer/plans/99/export-long-image").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        exportMvc.perform(post("/volunteer/plans/99/export-excel").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void protectedAiSkillsAndExportEndpoints_withHeaderSafetyCode_shouldReturn200() throws Exception {
        MockMvc aiMvc = MockMvcBuilders.standaloneSetup(
                        new AiAnalysisController(new FakeAiDeepAnalysisService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMvc skillsMvc = MockMvcBuilders.standaloneSetup(
                        new SkillsQaController(new FakeSkillsRagService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMvc exportMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerExportController(new FakeVolunteerExportService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        aiMvc.perform(get("/volunteer/plans/99/ai-analysis").header("X-Safety-Code", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(99));
        aiMvc.perform(post("/volunteer/plans/99/ai-analysis").header("X-Safety-Code", "SAFE1234").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(99));
        skillsMvc.perform(get("/volunteer/plans/99/skills/suggested-questions").header("X-Safety-Code", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("怎么调整"));
        skillsMvc.perform(post("/volunteer/plans/99/skills/ask")
                        .header("X-Safety-Code", "SAFE1234")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"怎么调整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("测试回答"));
        exportMvc.perform(post("/volunteer/plans/99/export-long-image").header("X-Safety-Code", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        exportMvc.perform(post("/volunteer/plans/99/export-excel").header("X-Safety-Code", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void aiAnalysis_withQuerySafetyCode_shouldPass() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new AiAnalysisController(new FakeAiDeepAnalysisService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/volunteer/plans/99/ai-analysis").param("safetyCode", "SAFE1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(99))
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    void skillsAsk_withBodyWrongSafetyCode_shouldReturn403() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new SkillsQaController(new FakeSkillsRagService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/plans/99/skills/ask")
                        .contentType(APPLICATION_JSON)
                        .content("{\"safetyCode\":\"WRONG99\",\"question\":\"怎么调整\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void exportLongImage_withBodySafetyCode_shouldPass() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new VolunteerExportController(new FakeVolunteerExportService(), new SafetyCodeRequestResolver(), new FakeSafetyCodeService(true)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/volunteer/plans/99/export-long-image")
                        .contentType(APPLICATION_JSON)
                        .content("{\"safetyCode\":\"SAFE1234\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void oldAccessKey_shouldStillVerifyPlanAccess() {
        PlanHistory history = new PlanHistory();
        history.setId(99L);
        SafetyCodeService service = safetyCodeService(history);

        String legacyAccessKey = service.buildLegacyAccessKey(99L);

        assertThat(service.verifyPlanAccess(99L, legacyAccessKey)).isTrue();
    }

    @Test
    void cardActivate_shouldReturnGone() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/auth/card-key/activate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cardKey\":\"AAAA-BBBB-CCCC-DDDD\",\"secretCode\":\"123456\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.message").value("卡密功能已下线，请使用安全码访问志愿方案。"));
    }

    private VolunteerRecommendController recommendController(FakeVolunteerService volunteerService) {
        SafetyCodeService safety = safetyCodeService(null);
        SafetyCodeIdentityService identity = new SafetyCodeIdentityService(
                Mockito.mock(SafetyCodeIdentityMapper.class), safety);
        RecommendEngineRouter router = Mockito.mock(RecommendEngineRouter.class);
        Mockito.when(router.resolve(Mockito.any(VolunteerService.GenerateRequest.class), Mockito.any(PolicyRuleConfig.class)))
                .thenReturn(RecommendEngineDecision.builder()
                        .engineName("OrdinaryParallelMajorEngine")
                        .recommendMode("PARALLEL_MAJOR")
                        .supportLevel("FULL_RECOMMEND")
                        .maxVolunteerCount(96)
                        .queryOnly(false)
                        .supportReason("测试普通推荐")
                        .build());
        return new VolunteerRecommendController(
                volunteerService,
                null,
                new ProvincePolicyService(),
                new FakePolicyRuleService(),
                new FakeMlPredictionService(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                router,
                Mockito.mock(QueryOnlyRecommendEngine.class),
                new SafetyCodeRequestResolver(),
                safety,
                identity,
                new AdmissionYearService());
    }

    private SafetyCodeService safetyCodeService(PlanHistory history) {
        SafetyCodeService service = new SafetyCodeService(planHistoryMapper(history));
        ReflectionTestUtils.setField(service, "jwtSecret", "unit-test-secret");
        return service;
    }

    private static PlanHistoryMapper planHistoryMapper(PlanHistory history) {
        return (PlanHistoryMapper) Proxy.newProxyInstance(
                PlanHistoryMapper.class.getClassLoader(),
                new Class<?>[]{PlanHistoryMapper.class},
                (proxy, method, args) -> "selectById".equals(method.getName()) ? history : null);
    }

    private static VolunteerService.PlanResult planResult(String safetyCode) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(99L);
        plan.setSafetyCode(safetyCode);
        plan.setAccessKey(safetyCode);
        plan.setTargetCount(96);
        plan.setItems(List.of());
        plan.setDiagnosis(Map.of("summary", "测试诊断"));
        return plan;
    }

    private static class FakeVolunteerService extends VolunteerService {
        private final VolunteerService.PlanResult plan;
        private boolean generateCalled;
        private Long lastUserId;
        private String lastRequestSafetyCode;

        FakeVolunteerService(VolunteerService.PlanResult plan) {
            super(null, null, null, null, null, null, new ObjectMapper(), null, new VolunteerMetricsRecorder(),
                    new SafetyCodeService(null), new ProvincePolicyService(), null, null, null, null, null, null,
                    new AdmissionYearService());
            this.plan = plan;
        }

        @Override
        public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req, Long userId, String clientIp) {
            this.generateCalled = true;
            this.lastUserId = userId;
            this.lastRequestSafetyCode = req == null ? null : req.getSafetyCode();
            return plan;
        }

        @Override
        public VolunteerService.PlanResult getPlanResult(Long planId, String accessKey) {
            return plan;
        }
    }

    private static class FakeSafetyCodeService extends SafetyCodeService {
        private final boolean valid;
        private String lastCode;

        FakeSafetyCodeService(boolean valid) {
            super(planHistoryMapper(null));
            this.valid = valid;
        }

        @Override
        public boolean verifyPlanAccess(Long planId, String code) {
            this.lastCode = code;
            return valid;
        }
    }

    private static class FakePolicyRuleService extends PolicyRuleService {
        FakePolicyRuleService() {
            super(null, new ProvincePolicyService(), new AdmissionYearService());
        }

        @Override
        public PolicyContext requirePolicy(String provinceCode, Integer year, String candidateType, String batchCode) {
            PolicyRuleConfig config = new PolicyRuleConfig();
            config.setProvince("GZ");
            config.setBatchCode("NORMAL_UNDERGRADUATE");
            config.setBatchName("普通本科批");
            config.setVolunteerMode("MAJOR_96");
            config.setMaxVolunteerCount(96);
            PolicyContext context = new PolicyContext();
            context.setConfig(config);
            return context;
        }

        @Override
        public String normalizeBatchCode(String batchCode) {
            return "NORMAL_UNDERGRADUATE";
        }

        @Override
        public Map<String, Object> toPublicPolicy(PolicyRuleConfig config) {
            return Map.of("province", "GZ");
        }
    }

    private static class FakeMlPredictionService extends MlPredictionService {
        FakeMlPredictionService() {
            super(new ObjectMapper(), null, new FallbackRulePredictionEngine(), new AdmissionYearService());
        }

        @Override
        public ApplyResult applyPredictions(VolunteerService.GenerateRequest req, VolunteerService.PlanResult plan, Integer policyMaxCount) {
            ApplyResult result = new ApplyResult();
            result.setModelVersion("test-model");
            return result;
        }
    }

    private static class FakeAiDeepAnalysisService extends AiDeepAnalysisService {
        FakeAiDeepAnalysisService() {
            super(null, null, null, new ObjectMapper(), null, null, null, null);
        }

        @Override
        public AiAnalysisVO get(Long planId, String accessKey) {
            AiAnalysisVO vo = new AiAnalysisVO();
            vo.setPlanId(planId);
            vo.setStatus("completed");
            return vo;
        }

        @Override
        public AiAnalysisVO generate(Long planId, String accessKey, boolean forceRefresh) {
            AiAnalysisVO vo = new AiAnalysisVO();
            vo.setPlanId(planId);
            vo.setStatus("completed");
            vo.setConclusion("测试结论");
            return vo;
        }
    }

    private static class FakeSkillsRagService extends SkillsRagService {
        FakeSkillsRagService() {
            super(null, null, null, null, new ObjectMapper(), null);
        }

        @Override
        public SkillsAnswer ask(Long planId, String accessKey, String question, String aiReport,
                                List<AiService.AdvisorSkillChatMessage> history) {
            SkillsAnswer answer = new SkillsAnswer();
            answer.setAnswer("测试回答");
            return answer;
        }

        @Override
        public List<String> suggestedQuestions() {
            return List.of("怎么调整");
        }
    }

    private static class FakeVolunteerExportService extends VolunteerExportService {
        FakeVolunteerExportService() {
            super(null, null);
        }

        @Override
        public byte[] exportLongImage(Long planId, String accessKey) {
            return new byte[]{1, 2, 3};
        }

        @Override
        public byte[] exportExcel(Long planId, String accessKey) {
            return new byte[]{4, 5, 6};
        }
    }
}
