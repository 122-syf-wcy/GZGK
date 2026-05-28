package com.gzly.controller;

import com.gzly.common.exception.GlobalExceptionHandler;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.service.DataReadinessService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvinceAlgorithmPolicyService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.VolunteerService;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VolunteerRecommendControllerTest {

    @Mock private VolunteerService volunteerService;
    @Mock private ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    @Mock private ProvincePolicyService provincePolicyService;
    private final ProvinceAlgorithmPolicyService provinceAlgorithmPolicyService = new ProvinceAlgorithmPolicyService();
    @Mock private PolicyRuleService policyRuleService;
    @Mock private MlPredictionService mlPredictionService;
    @Mock private DataReadinessService dataReadinessService;
    @Mock private JwtUtil jwtUtil;
    @Mock private PlanHistoryMapper planHistoryMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        VolunteerRecommendController controller = new VolunteerRecommendController(
                volunteerService,
                professionalGroupVolunteerService,
                provincePolicyService,
                provinceAlgorithmPolicyService,
                policyRuleService,
                mlPredictionService,
                dataReadinessService,
                jwtUtil,
                planHistoryMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void preOfficialDataMainBatchAllowsAnonymousEstimateRecommendation() throws Exception {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("HB");
        config.setYear(2026);
        config.setCandidateType("普通类");
        config.setBatchCode("HB_BENKE");
        config.setBatchName("本科普通批");
        config.setVolunteerMode("院校专业组（平行志愿）");

        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);

        when(provincePolicyService.normalizeProvinceCode("HB")).thenReturn("HB");
        when(policyRuleService.normalizeCandidateType("普通类")).thenReturn("普通类");
        when(policyRuleService.normalizeBatchCode("HB", "HB_BENKE")).thenReturn("HB_BENKE");
        when(policyRuleService.requirePolicy("HB", 2026, "普通类", "HB_BENKE")).thenReturn(context);
        when(dataReadinessService.get("HB", 2026)).thenReturn(preOfficialReadiness("HB"));
        when(dataReadinessService.isFullRecommendReady("HB", 2026)).thenReturn(false);
        when(provincePolicyService.getPolicy("HB")).thenReturn(provincePolicy("HB", "湖北"));
        when(provincePolicyService.isProfessionalGroupProvince("HB")).thenReturn(true);

        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode("HB");
        plan.setProvinceName("湖北");
        plan.setItems(java.util.List.of());
        when(professionalGroupVolunteerService.generate(org.mockito.ArgumentMatchers.any(), eq(null), anyString()))
                .thenReturn(plan);
        MlPredictionService.ApplyResult ml = new MlPredictionService.ApplyResult();
        ml.setModelVersion("fallback-rule-v1");
        ml.setFallbackUsed(true);
        when(mlPredictionService.applyPredictions(org.mockito.ArgumentMatchers.any(), eq(plan), eq(0))).thenReturn(ml);

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"HB\",\"year\":2026,\"candidateType\":\"普通类\",\"batchCode\":\"HB_BENKE\",\"score\":650,\"rank\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("HB"))
                .andExpect(jsonPath("$.data.warnings[0]").value("当前为PRE_OFFICIAL_DATA历史估算模式：仅基于已核验历史数据生成草稿，不代表2026官方招生计划、投档线或录取承诺。"));
    }

    @Test
    void missingYearShouldUsePublicRecommendYear2026ForEstimateRecommendation() throws Exception {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("HB");
        config.setYear(2026);
        config.setCandidateType("普通类");
        config.setBatchCode("HB_BENKE");
        config.setBatchName("本科普通批");
        config.setVolunteerMode("院校专业组（平行志愿）");

        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);

        when(provincePolicyService.normalizeProvinceCode("HB")).thenReturn("HB");
        when(policyRuleService.normalizeCandidateType("普通类")).thenReturn("普通类");
        when(policyRuleService.normalizeBatchCode("HB", "HB_BENKE")).thenReturn("HB_BENKE");
        when(policyRuleService.requirePolicy("HB", 2026, "普通类", "HB_BENKE")).thenReturn(context);
        when(dataReadinessService.get("HB", 2026)).thenReturn(preOfficialReadiness("HB"));
        when(dataReadinessService.isFullRecommendReady("HB", 2026)).thenReturn(false);
        when(provincePolicyService.getPolicy("HB")).thenReturn(provincePolicy("HB", "湖北"));
        when(provincePolicyService.isProfessionalGroupProvince("HB")).thenReturn(true);

        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode("HB");
        plan.setProvinceName("湖北");
        plan.setItems(java.util.List.of());
        when(professionalGroupVolunteerService.generate(org.mockito.ArgumentMatchers.any(), eq(null), anyString()))
                .thenReturn(plan);
        when(mlPredictionService.applyPredictions(org.mockito.ArgumentMatchers.any(), eq(plan), eq(0)))
                .thenReturn(new MlPredictionService.ApplyResult());

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"HB\",\"candidateType\":\"普通类\",\"batchCode\":\"HB_BENKE\",\"score\":650,\"rank\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(policyRuleService).requirePolicy("HB", 2026, "普通类", "HB_BENKE");
        verify(dataReadinessService).get("HB", 2026);
        verify(dataReadinessService).isFullRecommendReady("HB", 2026);
    }

    @Test
    void queryOnlyIdentityReturnsStrategyPlanWithoutConsumingGenerator() throws Exception {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("HB");
        config.setYear(2026);
        config.setCandidateType("艺术类");
        config.setBatchCode("HB_ART_XIAOKAO_BENKE");
        config.setBatchName("艺术本科校考批");
        config.setVolunteerMode("院校专业组单一志愿");

        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);

        when(provincePolicyService.normalizeProvinceCode("HB")).thenReturn("HB");
        when(policyRuleService.normalizeCandidateType("艺术类")).thenReturn("艺术类");
        when(policyRuleService.normalizeBatchCode("HB", "HB_ART_XIAOKAO_BENKE")).thenReturn("HB_ART_XIAOKAO_BENKE");
        when(policyRuleService.requirePolicy("HB", 2026, "艺术类", "HB_ART_XIAOKAO_BENKE")).thenReturn(context);
        when(dataReadinessService.get("HB", 2026)).thenReturn(preOfficialReadiness("HB"));
        when(dataReadinessService.isFullRecommendReady("HB", 2026)).thenReturn(false);
        when(provincePolicyService.getPolicy("HB")).thenReturn(provincePolicy("HB", "湖北"));
        when(policyRuleService.toPublicPolicy(config)).thenReturn(java.util.Map.of("province", "HB", "batchCode", "HB_ART_XIAOKAO_BENKE"));
        when(mlPredictionService.applyPredictions(any(), any(), eq(0))).thenReturn(new MlPredictionService.ApplyResult());
        assignPlanId(900L);
        when(volunteerService.buildPlanAccessKey(900L)).thenReturn("ACCESS-900");

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"HB\",\"year\":2026,\"candidateType\":\"艺术类\",\"batchCode\":\"HB_ART_XIAOKAO_BENKE\",\"score\":650,\"rank\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("HB"))
                .andExpect(jsonPath("$.data.supportLevel").value("QUERY_ONLY"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.accessKey").isNotEmpty());

        verify(professionalGroupVolunteerService, never()).generate(any(), any(), anyString());
    }

    @Test
    void queryOnlyBatchReturnsStrategyPlanWithoutConsumingGenerator() throws Exception {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("SC");
        config.setYear(2026);
        config.setCandidateType("普通类");
        config.setBatchCode("SC_TIQIAN_B");
        config.setBatchName("本科提前批 B 段");
        config.setVolunteerMode("院校专业组（平行志愿）");

        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);

        when(provincePolicyService.normalizeProvinceCode("SC")).thenReturn("SC");
        when(policyRuleService.normalizeCandidateType("普通类")).thenReturn("普通类");
        when(policyRuleService.normalizeBatchCode("SC", "SC_TIQIAN_B")).thenReturn("SC_TIQIAN_B");
        when(policyRuleService.requirePolicy("SC", 2026, "普通类", "SC_TIQIAN_B")).thenReturn(context);
        when(dataReadinessService.get("SC", 2026)).thenReturn(preOfficialReadiness("SC"));
        when(dataReadinessService.isFullRecommendReady("SC", 2026)).thenReturn(false);
        when(provincePolicyService.getPolicy("SC")).thenReturn(provincePolicy("SC", "四川"));
        when(policyRuleService.toPublicPolicy(config)).thenReturn(java.util.Map.of("province", "SC", "batchCode", "SC_TIQIAN_B"));
        when(mlPredictionService.applyPredictions(any(), any(), eq(0))).thenReturn(new MlPredictionService.ApplyResult());
        assignPlanId(901L);
        when(volunteerService.buildPlanAccessKey(901L)).thenReturn("ACCESS-901");

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"SC\",\"year\":2026,\"candidateType\":\"普通类\",\"batchCode\":\"SC_TIQIAN_B\",\"score\":650,\"rank\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("SC"))
                .andExpect(jsonPath("$.data.supportLevel").value("QUERY_ONLY"))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        verify(professionalGroupVolunteerService, never()).generate(any(), any(), anyString());
    }

    @Test
    void firstYearGapProvinceReturnsQueryOnlyPlanWithoutInventingItems() throws Exception {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("HA");
        config.setYear(2026);
        config.setCandidateType("普通类");
        config.setBatchCode("HA_BENKE");
        config.setBatchName("普通本科批");
        config.setVolunteerMode("院校专业组（策略建议）");
        config.setMaxVolunteerCount(0);
        config.setPolicyStatus("query_only_fallback");

        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);

        when(provincePolicyService.normalizeProvinceCode("HA")).thenReturn("HA");
        when(policyRuleService.normalizeCandidateType("普通类")).thenReturn("普通类");
        when(policyRuleService.normalizeBatchCode("HA", "HA_BENKE")).thenReturn("HA_BENKE");
        when(policyRuleService.requirePolicy("HA", 2026, "普通类", "HA_BENKE")).thenReturn(context);
        when(dataReadinessService.get("HA", 2026)).thenReturn(preOfficialReadiness("HA"));
        when(dataReadinessService.isFullRecommendReady("HA", 2026)).thenReturn(false);
        when(provincePolicyService.getPolicy("HA")).thenReturn(provincePolicy("HA", "河南"));
        when(policyRuleService.toPublicPolicy(config)).thenReturn(java.util.Map.of("province", "HA", "batchCode", "HA_BENKE"));
        when(mlPredictionService.applyPredictions(any(), any(), eq(0))).thenReturn(new MlPredictionService.ApplyResult());
        assignPlanId(999L);
        when(volunteerService.buildPlanAccessKey(999L)).thenReturn("ACCESS-999");

        mockMvc.perform(post("/volunteer/recommend")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provinceCode\":\"HA\",\"year\":2026,\"candidateType\":\"普通类\",\"batchCode\":\"HA_BENKE\",\"score\":585,\"rank\":33000,\"firstSubject\":\"物理\",\"selectedSubjects\":[\"物理\",\"化学\",\"生物\"],\"agreedDisclaimer\":true,\"disclaimerVersion\":\"2026-04-27-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provinceCode").value("HA"))
                .andExpect(jsonPath("$.data.responseProvince").value("HA"))
                .andExpect(jsonPath("$.data.supportLevel").value("QUERY_ONLY"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.accessKey").isNotEmpty())
                .andExpect(jsonPath("$.data.diagnosis.noCollegeListReason").value("QUERY_ONLY 阶段不生成假院校清单。"));

        verify(professionalGroupVolunteerService, never()).generate(any(), any(), anyString());
    }

    private void assignPlanId(long id) {
        doAnswer(invocation -> {
            com.gzly.entity.PlanHistory history = invocation.getArgument(0);
            history.setId(id);
            return 1;
        }).when(planHistoryMapper).insert(any(com.gzly.entity.PlanHistory.class));
    }

    private DataReadinessService.Readiness preOfficialReadiness(String provinceCode) {
        DataReadinessService.Readiness readiness = new DataReadinessService.Readiness();
        readiness.provinceCode = provinceCode;
        readiness.year = 2026;
        readiness.recommendationPhase = DataReadinessService.PRE_OFFICIAL_DATA;
        readiness.historicalTrainingReady = true;
        return readiness;
    }

    private ProvincePolicyService.ProvincePolicy provincePolicy(String provinceCode, String provinceName) {
        ProvincePolicyService.ProvincePolicy policy = new ProvincePolicyService.ProvincePolicy();
        policy.setProvinceCode(provinceCode);
        policy.setProvinceName(provinceName);
        policy.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        policy.setVolunteerUnitLabel("院校专业组");
        policy.setTargetBatch("本科普通批");
        policy.setTargetCount(45);
        policy.setOfficialSourceName(provinceName + "省教育考试院");
        return policy;
    }
}
