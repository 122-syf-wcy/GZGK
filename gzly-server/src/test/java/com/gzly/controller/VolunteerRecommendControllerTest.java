package com.gzly.controller;

import com.gzly.entity.PolicyRuleConfig;
import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.AdmissionYearService;
import com.gzly.service.BatchSupportService;
import com.gzly.service.MlPredictionService;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.ProvincePolicyService;
import com.gzly.service.SafetyCodeIdentityService;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.VolunteerService;
import com.gzly.service.recommend.QueryOnlyRecommendEngine;
import com.gzly.service.recommend.RecommendEngineDecision;
import com.gzly.service.recommend.RecommendEngineRouter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolunteerRecommendControllerTest {

    @Mock private VolunteerService volunteerService;
    @Mock private ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    @Mock private ProvincePolicyService provincePolicyService;
    @Mock private PolicyRuleService policyRuleService;
    @Mock private MlPredictionService mlPredictionService;
    @Mock private BatchSupportService batchSupportService;
    @Mock private RecommendEngineRouter recommendEngineRouter;
    @Mock private QueryOnlyRecommendEngine queryOnlyRecommendEngine;
    @Mock private SafetyCodeRequestResolver safetyCodeRequestResolver;
    @Mock private SafetyCodeService safetyCodeService;
    @Mock private SafetyCodeIdentityService safetyCodeIdentityService;

    private VolunteerRecommendController controller;
    private AdmissionYearService admissionYearService;

    @BeforeEach
    void setUp() {
        admissionYearService = new AdmissionYearService();
        admissionYearService.setActiveAdmissionYear(2026);
        admissionYearService.setHistoryYears("2025,2024");
        controller = new VolunteerRecommendController(
                volunteerService,
                professionalGroupVolunteerService,
                provincePolicyService,
                policyRuleService,
                mlPredictionService,
                batchSupportService,
                recommendEngineRouter,
                queryOnlyRecommendEngine,
                safetyCodeRequestResolver,
                safetyCodeService,
                safetyCodeIdentityService,
                admissionYearService);
    }

    @Test
    void decoratePlan_shouldUseDynamicSupportLevelForReadyNormalSpecialty() {
        VolunteerService.GenerateRequest req = request("NORMAL_SPECIALTY");
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        PolicyRuleService.PolicyContext policy = policy("NORMAL_SPECIALTY");
        Map<String, Object> publicPolicy = new LinkedHashMap<>();
        publicPolicy.put("supportLevel", "TRIAL_RECOMMEND");
        publicPolicy.put("recommendMode", "PARALLEL_MAJOR");
        when(policyRuleService.toPublicPolicy(policy.getConfig())).thenReturn(publicPolicy);
        BatchSupportService.BatchSupportItem item = supportItem("NORMAL_SPECIALTY", "普通类", "FULL_RECOMMEND", "动态门禁通过");
        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setRecommendationPhase(AdmissionYearService.PHASE_MODEL_RETRAINED);
        response.setOfficialDataReady(true);
        response.setDataReadiness(dataReadiness(AdmissionYearService.PHASE_MODEL_RETRAINED, true));
        response.setItems(List.of(item));
        when(batchSupportService.supportMatrix("GZ", 2026)).thenReturn(response);

        ReflectionTestUtils.invokeMethod(controller, "decoratePlan", req, plan, policy, MlPredictionService.ApplyResult.empty());

        assertThat(plan.getSupportLevel()).isEqualTo("FULL_RECOMMEND");
        assertThat(plan.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
        assertThat(plan.getSupportReason()).isEqualTo("动态门禁通过");
        assertThat(plan.getPolicy()).containsEntry("supportLevel", "FULL_RECOMMEND");
        assertThat(plan.getPolicy()).containsEntry("supportReason", "动态门禁通过");
    }

    @Test
    void decoratePlan_shouldKeepQueryOnlyBatchStaticAndSkipDynamicLookup() {
        VolunteerService.GenerateRequest req = request("EARLY_A_B");
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        PolicyRuleService.PolicyContext policy = policy("EARLY_A_B");
        Map<String, Object> publicPolicy = new LinkedHashMap<>();
        publicPolicy.put("supportLevel", "QUERY_ONLY");
        when(policyRuleService.toPublicPolicy(policy.getConfig())).thenReturn(publicPolicy);

        ReflectionTestUtils.invokeMethod(controller, "decoratePlan", req, plan, policy, MlPredictionService.ApplyResult.empty());

        assertThat(plan.getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(plan.getPolicy()).containsEntry("supportLevel", "QUERY_ONLY");
        verify(batchSupportService).supportMatrix("GZ", 2026);
    }

    @Test
    void decoratePlan_shouldDowngradeMainRankPlanBeforeOfficialData() {
        VolunteerService.GenerateRequest req = request("NORMAL_UNDERGRADUATE");
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        PolicyRuleService.PolicyContext policy = policy("NORMAL_UNDERGRADUATE");
        when(policyRuleService.toPublicPolicy(policy.getConfig())).thenReturn(new LinkedHashMap<>());
        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setActiveAdmissionYear(2026);
        response.setLatestOfficialDataYear(2025);
        response.setTargetYear(2026);
        response.setFutureImportYear(2026);
        response.setTrainingYears(List.of(2024, 2025));
        response.setDataSourceYears(List.of(2024, 2025));
        response.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        response.setEstimateMode(true);
        response.setOfficialDataReady(false);
        response.setItems(List.of(supportItem("NORMAL_UNDERGRADUATE", "普通类", "FULL_RECOMMEND", "动态门禁通过")));
        when(batchSupportService.supportMatrix("GZ", 2026)).thenReturn(response);

        ReflectionTestUtils.invokeMethod(controller, "decoratePlan", req, plan, policy, MlPredictionService.ApplyResult.empty());

        assertThat(plan.getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(plan.getRecommendMode()).isEqualTo("QUERY_ONLY");
        assertThat(plan.getEngineName()).isEqualTo(QueryOnlyRecommendEngine.NAME);
        assertThat(plan.isEstimateMode()).isTrue();
        assertThat(plan.isOfficialDataReady()).isFalse();
        assertThat(plan.getWarnings()).contains(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
    }

    @Test
    void decoratePlan_shouldDowngradeFullToTrialAfterOfficialImportBeforeRetrain() {
        VolunteerService.GenerateRequest req = request("NORMAL_UNDERGRADUATE");
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        PolicyRuleService.PolicyContext policy = policy("NORMAL_UNDERGRADUATE");
        when(policyRuleService.toPublicPolicy(policy.getConfig())).thenReturn(new LinkedHashMap<>());
        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setRecommendationPhase(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED);
        response.setOfficialDataReady(true);
        response.setDataReadiness(dataReadiness(AdmissionYearService.PHASE_OFFICIAL_DATA_IMPORTED, false));
        response.setItems(List.of(supportItem("NORMAL_UNDERGRADUATE", "普通类", "FULL_RECOMMEND", "动态门禁通过")));
        when(batchSupportService.supportMatrix("GZ", 2026)).thenReturn(response);

        ReflectionTestUtils.invokeMethod(controller, "decoratePlan", req, plan, policy, MlPredictionService.ApplyResult.empty());

        assertThat(plan.getSupportLevel()).isEqualTo("TRIAL_RECOMMEND");
        assertThat(plan.getRecommendMode()).isEqualTo("PARALLEL_MAJOR");
        assertThat(plan.getEngineName()).isEqualTo("OrdinaryParallelMajorEngine");
        assertThat(plan.getSupportReason()).isEqualTo(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        assertThat(plan.getWarnings()).contains(AdmissionYearService.OFFICIAL_DATA_IMPORTED_TRIAL_WARNING);
        assertThat(plan.getPolicy()).containsEntry("supportLevel", "TRIAL_RECOMMEND");
    }

    @Test
    void queryOnly_shouldNotCreatePlanHistory() {
        VolunteerService.GenerateRequest req = request("EARLY_A_B");
        RecommendEngineDecision decision = queryOnlyDecision("EARLY_A_B", "SequentialCollegeEngine", "SEQUENTIAL_COLLEGE", 1);
        VolunteerService.PlanResult queryOnlyPlan = queryOnlyPlan("EARLY_A_B", 1);
        stubQueryOnlyFlow(req, decision, queryOnlyPlan);

        Result<VolunteerService.PlanResult> response = controller.recommend(req, new MockHttpServletRequest());

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getId()).isZero();
        verify(volunteerService, never()).generate(any(), any(), any());
        verify(mlPredictionService, never()).applyPredictions(any(), any(), any());
    }

    @Test
    void earlyAB_shouldNotReturn96ParallelItems() {
        VolunteerService.GenerateRequest req = request("EARLY_A_B");
        RecommendEngineDecision decision = queryOnlyDecision("EARLY_A_B", "SequentialCollegeEngine", "SEQUENTIAL_COLLEGE", 1);
        VolunteerService.PlanResult queryOnlyPlan = queryOnlyPlan("EARLY_A_B", 1);
        stubQueryOnlyFlow(req, decision, queryOnlyPlan);

        Result<VolunteerService.PlanResult> response = controller.recommend(req, new MockHttpServletRequest());

        assertThat(response.getData().getId()).isZero();
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getTargetCount()).isEqualTo(1);
        assertThat(response.getData().getRecommendMode()).isEqualTo("SEQUENTIAL_COLLEGE");
    }

    @Test
    void artWithoutCompositeData_shouldQueryOnly() {
        VolunteerService.GenerateRequest req = request("ART_UNDERGRADUATE_B", "艺术类");
        RecommendEngineDecision decision = queryOnlyDecision("ART_UNDERGRADUATE_B", "ArtCompositeRecommendEngine", "ART_COMPOSITE", 60);
        VolunteerService.PlanResult queryOnlyPlan = queryOnlyPlan("ART_UNDERGRADUATE_B", 60);
        stubQueryOnlyFlow(req, decision, queryOnlyPlan);

        Result<VolunteerService.PlanResult> response = controller.recommend(req, new MockHttpServletRequest());

        assertThat(response.getData().getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getEngineName()).isEqualTo("ArtCompositeRecommendEngine");
        verify(volunteerService, never()).generate(any(), any(), any());
    }

    @Test
    void sportsWithoutCompositeData_shouldQueryOnly() {
        VolunteerService.GenerateRequest req = request("SPORTS_UNDERGRADUATE", "体育类");
        RecommendEngineDecision decision = queryOnlyDecision("SPORTS_UNDERGRADUATE", "SportsCompositeRecommendEngine", "SPORTS_COMPOSITE", 60);
        VolunteerService.PlanResult queryOnlyPlan = queryOnlyPlan("SPORTS_UNDERGRADUATE", 60);
        stubQueryOnlyFlow(req, decision, queryOnlyPlan);

        Result<VolunteerService.PlanResult> response = controller.recommend(req, new MockHttpServletRequest());

        assertThat(response.getData().getSupportLevel()).isEqualTo("QUERY_ONLY");
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getEngineName()).isEqualTo("SportsCompositeRecommendEngine");
        verify(volunteerService, never()).generate(any(), any(), any());
    }

    @Test
    void recommend_shouldRejectHistoricalYearForPublicEntry() {
        VolunteerService.GenerateRequest req = request("NORMAL_UNDERGRADUATE");
        req.setYear(2025);
        when(safetyCodeRequestResolver.resolve(any(HttpServletRequest.class), any(VolunteerService.GenerateRequest.class))).thenReturn("SAFE1234");
        when(safetyCodeService.normalizeSafetyCode("SAFE1234")).thenReturn("SAFE1234");
        when(provincePolicyService.normalizeProvinceCode("GZ")).thenReturn("GZ");
        when(policyRuleService.normalizeBatchCode(req.getBatchCode())).thenReturn(req.getBatchCode());

        assertThatThrownBy(() -> controller.recommend(req, new MockHttpServletRequest()))
                .isInstanceOf(BizException.class)
                .hasMessage(AdmissionYearService.PUBLIC_YEAR_LOCKED_MESSAGE);
        verify(policyRuleService, never()).requirePolicy(any(), any(), any(), any());
        verify(volunteerService, never()).generate(any(), any(), any());
    }

    private VolunteerService.GenerateRequest request(String batchCode) {
        return request(batchCode, "普通类");
    }

    private VolunteerService.GenerateRequest request(String batchCode, String candidateType) {
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setProvinceCode("GZ");
        req.setYear(2026);
        req.setCandidateType(candidateType);
        req.setBatchCode(batchCode);
        return req;
    }

    private PolicyRuleService.PolicyContext policy(String batchCode) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince("GZ");
        config.setYear(2026);
        config.setCandidateType("普通类");
        config.setBatchCode(batchCode);
        config.setBatchName(batchCode);
        config.setVolunteerMode("专业（类）+ 院校");
        config.setMaxVolunteerCount(96);
        PolicyRuleService.PolicyContext context = new PolicyRuleService.PolicyContext();
        context.setConfig(config);
        return context;
    }

    private BatchSupportService.BatchSupportItem supportItem(String batchCode, String candidateType,
                                                             String supportLevel, String supportReason) {
        BatchSupportService.BatchSupportItem item = new BatchSupportService.BatchSupportItem();
        item.setBatchCode(batchCode);
        item.setCandidateType(candidateType);
        item.setSupportLevel(supportLevel);
        item.setSupportReason(supportReason);
        return item;
    }

    private BatchSupportService.DataReadiness dataReadiness(String phase, boolean mlReady) {
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        readiness.setPolicyReady(true);
        readiness.setScoreSegmentReady(true);
        readiness.setAdmissionPlanReady(true);
        readiness.setMajorRequirementReady(true);
        readiness.setMajorMetaReady(true);
        readiness.setMlTrainingReady(mlReady);
        readiness.setHistoricalTrainingReady(true);
        readiness.setRecommendationPhase(phase);
        return readiness;
    }

    private RecommendEngineDecision queryOnlyDecision(String batchCode, String engineName, String recommendMode, int maxVolunteerCount) {
        return RecommendEngineDecision.builder()
                .engineName(engineName)
                .recommendMode(recommendMode)
                .supportLevel("QUERY_ONLY")
                .maxVolunteerCount(maxVolunteerCount)
                .queryOnly(true)
                .batchCode(batchCode)
                .supportReason("仅支持政策和数据缺口说明")
                .warnings(List.of("仅支持政策和数据缺口说明"))
                .build();
    }

    private VolunteerService.PlanResult queryOnlyPlan(String batchCode, int targetCount) {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(0L);
        plan.setTargetBatch(batchCode);
        plan.setTargetCount(targetCount);
        plan.setItems(List.of());
        plan.setSupportLevel("QUERY_ONLY");
        return plan;
    }

    private void stubQueryOnlyFlow(VolunteerService.GenerateRequest req,
                                   RecommendEngineDecision decision,
                                   VolunteerService.PlanResult plan) {
        PolicyRuleService.PolicyContext context = policy(req.getBatchCode(), req.getCandidateType());
        when(safetyCodeRequestResolver.resolve(any(HttpServletRequest.class), any(VolunteerService.GenerateRequest.class))).thenReturn("SAFE1234");
        when(safetyCodeService.normalizeSafetyCode("SAFE1234")).thenReturn("SAFE1234");
        when(provincePolicyService.normalizeProvinceCode("GZ")).thenReturn("GZ");
        when(policyRuleService.normalizeBatchCode(req.getBatchCode())).thenReturn(req.getBatchCode());
        when(policyRuleService.requirePolicy(eq("GZ"), eq(2026), eq(req.getCandidateType()), eq(req.getBatchCode()))).thenReturn(context);
        when(recommendEngineRouter.resolve(any(VolunteerService.GenerateRequest.class), eq(context.getConfig()))).thenReturn(decision);
        when(queryOnlyRecommendEngine.generate(any(VolunteerService.GenerateRequest.class), eq(context.getConfig()), eq(decision))).thenReturn(plan);
        when(policyRuleService.toPublicPolicy(context.getConfig())).thenReturn(new LinkedHashMap<>());
    }

    private PolicyRuleService.PolicyContext policy(String batchCode, String candidateType) {
        PolicyRuleService.PolicyContext context = policy(batchCode);
        context.getConfig().setCandidateType(candidateType);
        return context;
    }
}
