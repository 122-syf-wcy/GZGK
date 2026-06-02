package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.service.VolunteerService.GenerateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VolunteerServiceComplianceTest {

    private VolunteerService service;
    private Method validateGenerateRequest;
    private Method toPreferenceProfile;

    @BeforeEach
    void setUp() throws Exception {
        service = new VolunteerService(
                null, null, null, null, null,
                null, new ObjectMapper(), null, new VolunteerMetricsRecorder(), new SafetyCodeService(), new ProvincePolicyService(), null,
                null, null, null, null, null);
        validateGenerateRequest = VolunteerService.class.getDeclaredMethod("validateGenerateRequest", GenerateRequest.class);
        validateGenerateRequest.setAccessible(true);
        toPreferenceProfile = VolunteerService.class.getDeclaredMethod("toPreferenceProfile", GenerateRequest.class);
        toPreferenceProfile.setAccessible(true);
    }

    @Test
    void validateGenerateRequest_rejectsMissingDisclaimerConfirmation() {
        GenerateRequest req = validRequest();
        req.setAgreedDisclaimer(null);

        Throwable error = invokeValidate(req);

        assertThat(error)
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
    }

    @Test
    void validateGenerateRequest_rejectsFalseDisclaimerConfirmation() {
        GenerateRequest req = validRequest();
        req.setAgreedDisclaimer(false);

        Throwable error = invokeValidate(req);

        assertThat(error)
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
    }

    @Test
    void validateGenerateRequest_rejectsBlankOrMismatchedDisclaimerVersion() {
        GenerateRequest blank = validRequest();
        blank.setDisclaimerVersion("");
        GenerateRequest mismatch = validRequest();
        mismatch.setDisclaimerVersion("2026-04-27-old");

        assertThat(invokeValidate(blank))
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
        assertThat(invokeValidate(mismatch))
                .isInstanceOf(BizException.class)
                .hasMessage(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
    }

    @Test
    void validateGenerateRequest_acceptsCurrentDisclaimerVersion() {
        assertThat(invokeValidate(validRequest())).isNull();
    }

    @Test
    void resolveRankResolution_usesOfficialConservativeRankWhenRankMissing() throws Exception {
        ProvinceRankService rankService = mock(ProvinceRankService.class);
        VolunteerService serviceWithRank = new VolunteerService(
                null, null, null, null, null,
                null, new ObjectMapper(), null, new VolunteerMetricsRecorder(), new SafetyCodeService(), new ProvincePolicyService(), rankService,
                null, null, null, null, null);
        GenerateRequest req = validRequest();
        req.setProvinceRank(0);
        AlgorithmService.RankEstimate estimate = new AlgorithmService.RankEstimate();
        estimate.setScore(560);
        estimate.setSubjectType("物理类");
        estimate.setEstimatedRank(34_567);
        estimate.setRankLow(34_120);
        estimate.setRankHigh(34_567);
        estimate.setDataPoints(1);
        estimate.setReferenceYear(2025);
        estimate.setConfidence("官方");
        estimate.setSource("贵州省招生考试院");
        estimate.setNote("官方一分一段表命中");
        when(rankService.estimateRank("GZ", 560, "物理类", null)).thenReturn(estimate);

        Method resolve = VolunteerService.class.getDeclaredMethod(
                "resolveRankResolution", String.class, GenerateRequest.class, String.class);
        resolve.setAccessible(true);
        Object resolution = resolve.invoke(serviceWithRank, "GZ", req, "物理类");
        Method effectiveRank = resolution.getClass().getDeclaredMethod("effectiveRank");
        Method summaryMethod = resolution.getClass().getDeclaredMethod("summary");
        effectiveRank.setAccessible(true);
        summaryMethod.setAccessible(true);
        VolunteerService.RankEstimateSummary summary =
                (VolunteerService.RankEstimateSummary) summaryMethod.invoke(resolution);

        assertThat(effectiveRank.invoke(resolution)).isEqualTo(34_567);
        assertThat(summary.isRankEstimated()).isTrue();
        assertThat(summary.getSubmittedRank()).isNull();
        assertThat(summary.getEffectiveRank()).isEqualTo(34_567);
        assertThat(summary.getRankLow()).isEqualTo(34_120);
        assertThat(summary.getRankHigh()).isEqualTo(34_567);
        assertThat(summary.getReminder()).contains("保守位次").contains("贵州省招生考试院");
    }

    @Test
    void buildRequestSnapshot_preservesDisclaimerConfirmation() throws Exception {
        GenerateRequest req = validRequest();
        Method toProfile = VolunteerService.class.getDeclaredMethod("toPreferenceProfile", GenerateRequest.class);
        toProfile.setAccessible(true);
        Object profile = toProfile.invoke(service, req);
        Method buildSnapshot = VolunteerService.class.getDeclaredMethod("buildRequestSnapshot", GenerateRequest.class, profile.getClass());
        buildSnapshot.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) buildSnapshot.invoke(service, req, profile);

        assertThat(snapshot)
                .containsEntry("agreedDisclaimer", true)
                .containsEntry("disclaimerVersion", ComplianceConstants.DISCLAIMER_VERSION);
    }

    @Test
    void resolveGradientRanges_usesBalancedPresetByDefault() throws Exception {
        GenerateRequest req = validRequest();

        VolunteerService.GradientRangeSummary summary = invokeResolveRangeSummary(req);

        assertThat(summary.getSource()).isEqualTo("preset");
        assertThat(summary.getRanges().get("冲").getRankLow()).isEqualTo(22000);
        assertThat(summary.getRanges().get("冲").getRankHigh()).isEqualTo(29000);
        assertThat(summary.getRanges().get("稳").getRankHigh()).isEqualTo(36000);
        assertThat(summary.getRanges().get("冲").getTargetCount()).isEqualTo(19);
        assertThat(summary.getRanges().get("稳").getTargetCount()).isEqualTo(38);
        assertThat(summary.getRanges().get("保").getTargetCount()).isEqualTo(29);
        assertThat(summary.getRanges().get("垫").getTargetCount()).isEqualTo(10);
    }

    @Test
    void resolveGradientRanges_highRankUsesAdaptiveRatioWindow() throws Exception {
        GenerateRequest req = validRequest();
        req.setTotalScore(620);
        req.setProvinceRank(3601);

        VolunteerService.GradientRangeSummary summary = invokeResolveRangeSummary(req);

        assertThat(summary.getRanges().get("冲").getRankLow()).isEqualTo(2341);
        assertThat(summary.getRanges().get("冲").getRankHigh()).isEqualTo(3421);
        assertThat(summary.getRanges().get("稳").getRankLow()).isEqualTo(3421);
        assertThat(summary.getRanges().get("稳").getRankHigh()).isEqualTo(4321);
        assertThat(summary.getRanges().get("冲").getRangeSourceNote()).contains("高分段");
    }

    @Test
    void resolveGradientRanges_acceptsCustomRanges() throws Exception {
        GenerateRequest req = validRequest();
        req.setGradientRanges(Map.of(
                "chong", range(-9000, -3000),
                "wen", range(-3000, 4500),
                "bao", range(4500, 13000),
                "dian", range(13000, 28000)));

        VolunteerService.GradientRangeSummary summary = invokeResolveRangeSummary(req);

        assertThat(summary.getSource()).isEqualTo("custom");
        assertThat(summary.getRanges().get("冲").getRankLow()).isEqualTo(23000);
        assertThat(summary.getRanges().get("垫").getRankHigh()).isEqualTo(60000);
    }

    @Test
    void resolveGradientRanges_rejectsInvalidCustomRanges() {
        GenerateRequest req = validRequest();
        req.setGradientRanges(Map.of(
                "chong", range(-1000, 2000),
                "wen", range(-3000, 3000),
                "bao", range(3000, 10000),
                "dian", range(10000, 25000)));

        Throwable error = invokeResolve(req);

        assertThat(error)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("冲档区间");
    }

    @Test
    void specialTypeReason_detectsSpecialAdmissionsForDefaultExclusion() throws Exception {
        Method method = VolunteerService.class.getDeclaredMethod(
                "specialTypeReason",
                com.gzly.entity.University.class, String.class, String.class, String.class);
        method.setAccessible(true);

        String nationalPlan = (String) method.invoke(service, null, "南京大学", "计算机科学与技术（国家专项计划）", "本科批");
        String police = (String) method.invoke(service, null, "中国人民公安大学", "公安情报学", "本科批");
        String normal = (String) method.invoke(service, null, "贵州大学", "计算机类", "本科批");

        assertThat(nationalPlan).contains("专项");
        assertThat(police).contains("军警公安");
        assertThat(normal).isBlank();
    }

    private Throwable invokeValidate(GenerateRequest req) {
        try {
            validateGenerateRequest.invoke(service, req);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    private VolunteerService.GradientRangeSummary invokeResolveRangeSummary(GenerateRequest req) throws Exception {
        Object profile = toPreferenceProfile.invoke(service, req);
        Method resolve = VolunteerService.class.getDeclaredMethod(
                "resolveGradientRanges", GenerateRequest.class, int.class, profile.getClass());
        resolve.setAccessible(true);
        Object resolved = resolve.invoke(service, req, req.getProvinceRank(), profile);
        Method summary = resolved.getClass().getDeclaredMethod("summary");
        summary.setAccessible(true);
        return (VolunteerService.GradientRangeSummary) summary.invoke(resolved);
    }

    private Throwable invokeResolve(GenerateRequest req) {
        try {
            invokeResolveRangeSummary(req);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    private VolunteerService.GradientRangeInput range(int min, int max) {
        VolunteerService.GradientRangeInput range = new VolunteerService.GradientRangeInput();
        range.setRankOffsetMin(min);
        range.setRankOffsetMax(max);
        return range;
    }

    private GenerateRequest validRequest() {
        GenerateRequest req = new GenerateRequest();
        req.setTotalScore(560);
        req.setProvinceRank(32000);
        req.setFirstSubject("物理");
        req.setResubjects(List.of("化学", "生物"));
        req.setAgreedDisclaimer(true);
        req.setDisclaimerVersion(ComplianceConstants.DISCLAIMER_VERSION);
        return req;
    }
}
