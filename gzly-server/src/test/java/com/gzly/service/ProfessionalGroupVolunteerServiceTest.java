package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.ProfessionalGroupAlgorithmEnricher;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataAdmissionGroupPlanMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.service.VolunteerService.GenerateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * ProfessionalGroupVolunteerService（院校专业组 45 志愿链路）首批直接单测。
 *
 * <p>本类此前零直接测试（仅被 RecommendationOrchestratorTest 以 mock 引用）。这里覆盖三处
 * 关键私有逻辑，全部采用「锁定现状」策略断言当前真实行为，不修改生产代码：</p>
 * <ol>
 *   <li>{@link #resolveTargetTotal} —— 云南专项/少数民族预科加 10 个配额；</li>
 *   <li>{@link #validate} —— 海南 3+3 标准分 900 制分数区间与 3 门选考校验、3+1+2 的首选/再选校验；</li>
 *   <li>{@link #parseGradientPreset} —— 梯度 preset JSON 解析与总和校验。</li>
 * </ol>
 *
 * <p>三个目标方法均为 private，沿用项目既有 {@code ReflectionTestUtils} 反射调用风格
 * （见 VolunteerServiceGenerateIntegrationTest / RecommendationOrchestratorTest）。
 * 构造服务时桩出全部外部依赖，仅注入真实 ObjectMapper 供 JSON 解析使用。</p>
 */
class ProfessionalGroupVolunteerServiceTest {

    private ProvincePolicyService provincePolicyService;
    private ProfessionalGroupVolunteerService service;

    @BeforeEach
    void setUp() {
        provincePolicyService = new ProvincePolicyService();
        service = new ProfessionalGroupVolunteerService(
                mock(DataAdmissionGroupLineMapper.class),
                mock(DataAdmissionGroupPlanMapper.class),
                mock(DataScoreRankMapper.class),
                mock(PlanHistoryMapper.class),
                mock(ScoreLineService.class),
                provincePolicyService,
                mock(ProvinceRankService.class),
                new ObjectMapper(),
                new VolunteerMetricsRecorder(),
                new SafetyCodeService(),
                mock(ProfessionalGroupAlgorithmEnricher.class));
    }

    // ══════════════════ 1. YN 云南配额（resolveTargetTotal） ══════════════════

    @Test
    void resolveTargetTotal_yunnanBaseWithoutTags_usesPolicyCount() {
        GenerateRequest req = baseRequest("YN");
        // 云南政策库 targetCount = 40，未携带任何资格标签 → 不加额
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(40);
    }

    @Test
    void resolveTargetTotal_yunnanSpecialProgram_adds10() {
        GenerateRequest req = baseRequest("YN");
        req.setQualificationTags(List.of("国家专项"));
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(50);
    }

    @Test
    void resolveTargetTotal_yunnanMinorityPrep_adds10() {
        GenerateRequest req = baseRequest("YN");
        req.setQualificationTags(List.of("少数民族预科"));
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(50);
    }

    @Test
    void resolveTargetTotal_yunnanSpecialAndMinority_adds20() {
        GenerateRequest req = baseRequest("YN");
        // 专项与预科两个分支同时命中 → base + 10 + 10
        req.setQualificationTags(List.of("地方专项", "预科"));
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(60);
    }

    @Test
    void resolveTargetTotal_nonYunnanProvinceIgnoresTags() {
        GenerateRequest req = baseRequest("SC");
        // 四川政策库 targetCount = 45；加额分支仅对云南生效
        req.setQualificationTags(List.of("国家专项", "少数民族预科"));
        assertThat(resolveTargetTotal("SC", req)).isEqualTo(45);
    }

    @Test
    void resolveTargetTotal_policyMaxVolunteerCountOverridesBase() {
        GenerateRequest req = baseRequest("YN");
        req.setPolicyMaxVolunteerCount(30);
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(30);
        req.setQualificationTags(List.of("高校专项"));
        assertThat(resolveTargetTotal("YN", req)).isEqualTo(40);
    }

    // ══════════════════ 2. 海南 3+3 与 900 分校验（validate） ══════════════════

    @Test
    void validate_hainanStandard900ScoreBelow100_throws() {
        GenerateRequest req = hainanRequest(99, List.of("物理", "化学", "生物"));
        assertThatThrownBy(() -> invokeValidate("HI", req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标准分900分制");
    }

    @Test
    void validate_hainanStandard900ScoreAbove900_throws() {
        GenerateRequest req = hainanRequest(901, List.of("物理", "化学", "生物"));
        assertThatThrownBy(() -> invokeValidate("HI", req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标准分900分制");
    }

    @Test
    void validate_hainanStandard900ValidRequest_passes() {
        GenerateRequest req = hainanRequest(700, List.of("物理", "化学", "生物"));
        // 不抛异常即为通过
        invokeValidate("HI", req);
    }

    @Test
    void validate_hainanRequiresExactlyThreeSelectedSubjects_throws() {
        GenerateRequest req = hainanRequest(700, List.of("物理", "化学"));
        assertThatThrownBy(() -> invokeValidate("HI", req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("须提供3门选考科目");
    }

    @Test
    void validate_hainanFallsBackToResubjectsWhenSelectedSubjectsEmpty() {
        GenerateRequest req = hainanRequest(700, null);
        req.setResubjects(List.of("物理", "化学", "生物"));
        invokeValidate("HI", req);
    }

    @Test
    void validate_threeOneTwoRejectsIllegalFirstSubject_throws() {
        GenerateRequest req = baseRequest("SC");
        req.setFirstSubject("化学");
        assertThatThrownBy(() -> invokeValidate("SC", req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("首选科目必须为物理或历史");
    }

    @Test
    void validate_threeOneTwoRequiresTwoResubjects_throws() {
        GenerateRequest req = baseRequest("SC");
        req.setResubjects(List.of("化学"));
        assertThatThrownBy(() -> invokeValidate("SC", req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("再选科目必须选择2门");
    }

    @Test
    void validate_missingDisclaimerConfirmation_throws() {
        GenerateRequest req = baseRequest("SC");
        req.setAgreedDisclaimer(false);
        assertThatThrownBy(() -> invokeValidate("SC", req))
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
    }

    @Test
    void validate_disclaimerVersionMismatch_throws() {
        GenerateRequest req = baseRequest("SC");
        req.setDisclaimerVersion("v0-outdated");
        assertThatThrownBy(() -> invokeValidate("SC", req))
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
    }

    @Test
    void validate_nullRequest_throws() {
        assertThatThrownBy(() -> invokeValidate("SC", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请求参数不能为空");
    }

    // ══════════════════ 3. 梯度 preset JSON（parseGradientPreset） ══════════════════

    @Test
    void parseGradientPreset_validTotalMatch_returnsCounts() {
        Map<String, Integer> result = parsePreset("{\"counts\":{\"冲\":15,\"稳\":15,\"保\":11,\"垫\":4}}", 45);
        assertThat(result)
                .containsEntry("冲", 15).containsEntry("稳", 15)
                .containsEntry("保", 11).containsEntry("垫", 4)
                .hasSize(4);
    }

    @Test
    void parseGradientPreset_missingGradientDefaultsToZero() {
        // 只给三个梯度，缺省的档位按 0 计；总和仍需等于目标总数
        Map<String, Integer> result = parsePreset("{\"counts\":{\"冲\":5,\"稳\":5}}", 10);
        assertThat(result)
                .containsEntry("冲", 5).containsEntry("稳", 5)
                .containsEntry("保", 0).containsEntry("垫", 0);
    }

    @Test
    void parseGradientPreset_sumMismatch_returnsNull() {
        // 总和 44 != 45 → 视为无效预设，返回 null 交由策略模式分配
        assertThat(parsePreset("{\"counts\":{\"冲\":15,\"稳\":15,\"保\":10,\"垫\":4}}", 45)).isNull();
    }

    @Test
    void parseGradientPreset_countsNotObject_returnsNull() {
        assertThat(parsePreset("{\"counts\":123}", 45)).isNull();
    }

    @Test
    void parseGradientPreset_negativeValue_returnsNull() {
        assertThat(parsePreset("{\"counts\":{\"冲\":-1,\"稳\":16,\"保\":16,\"垫\":14}}", 45)).isNull();
    }

    @Test
    void parseGradientPreset_blankOrNull_returnsNull() {
        assertThat(parsePreset(null, 45)).isNull();
        assertThat(parsePreset("   ", 45)).isNull();
    }

    @Test
    void parseGradientPreset_malformedJson_returnsNull() {
        assertThat(parsePreset("{counts: 冲=15}", 45)).isNull();
    }

    // ══════════════════ helpers ══════════════════

    private void invokeValidate(String provinceCode, GenerateRequest req) {
        ReflectionTestUtils.invokeMethod(service, "validate", provincePolicyService.getPolicy(provinceCode), req);
    }

    private Integer resolveTargetTotal(String provinceCode, GenerateRequest req) {
        return (Integer) ReflectionTestUtils.invokeMethod(service, "resolveTargetTotal",
                provincePolicyService.getPolicy(provinceCode), req);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parsePreset(String json, int targetTotal) {
        return (Map<String, Integer>) ReflectionTestUtils.invokeMethod(service, "parseGradientPreset", json, targetTotal);
    }

    /** 3+1+2 省份的合法基础请求（总分 560、物理、化学生物、已确认免责声明）。 */
    private GenerateRequest baseRequest(String provinceCode) {
        GenerateRequest req = new GenerateRequest();
        req.setProvinceCode(provinceCode);
        req.setTotalScore(560);
        req.setProvinceRank(32000);
        req.setFirstSubject("物理");
        req.setResubjects(List.of("化学", "生物"));
        req.setAgreedDisclaimer(true);
        req.setDisclaimerVersion(ComplianceConstants.DISCLAIMER_VERSION);
        return req;
    }

    /** 海南 3+3 请求：只设置影响 validate 的字段（首选科目在 3+3 下不参与校验）。 */
    private GenerateRequest hainanRequest(int totalScore, List<String> selectedSubjects) {
        GenerateRequest req = new GenerateRequest();
        req.setProvinceCode("HI");
        req.setTotalScore(totalScore);
        req.setSelectedSubjects(selectedSubjects);
        req.setAgreedDisclaimer(true);
        req.setDisclaimerVersion(ComplianceConstants.DISCLAIMER_VERSION);
        return req;
    }
}
