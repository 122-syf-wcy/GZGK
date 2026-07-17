package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.entity.MajorPlannerResult;
import com.gzly.mapper.MajorPlannerResultMapper;
import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MajorPlannerServiceTest {

    private MajorPlannerResultMapper mapper;
    private MajorPlannerCodeService codeService;
    private CredentialAttemptLimiter credentialAttemptLimiter;
    private MajorPlannerService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MajorPlannerResultMapper.class);
        codeService = new MajorPlannerCodeService();
        ReflectionTestUtils.setField(codeService, "jwtSecret", "unit-test-secret-key-at-least-32-characters-long");
        credentialAttemptLimiter = new CredentialAttemptLimiter();
        AiService aiService = mock(AiService.class);
        service = new MajorPlannerService(mapper, codeService, credentialAttemptLimiter, aiService, new ObjectMapper());
    }

    @Test
    void evaluate_shouldReturnTopTenAndStoreHashOnly() {
        when(mapper.insert(any(MajorPlannerResult.class))).thenAnswer(invocation -> {
            MajorPlannerResult row = invocation.getArgument(0);
            row.setId(12L);
            return 1;
        });
        MajorPlannerService.EvaluateRequest req = request();

        MajorPlannerService.MajorPlannerView view = service.evaluate(req);

        assertThat(view.getId()).isEqualTo(12L);
        assertThat(view.getPlanCode()).isNotBlank();
        assertThat(view.getResult().getTopMajors()).hasSize(10);
        assertThat(view.getResult().getTopMajors().get(0).getMatchScore()).isGreaterThan(70);
        assertThat(view.getResult().getRadar()).isNotEmpty();
        assertThat(view.getResult().getDisclaimer()).contains("仅供参考");

        ArgumentCaptor<MajorPlannerResult> captor = ArgumentCaptor.forClass(MajorPlannerResult.class);
        org.mockito.Mockito.verify(mapper).insert(captor.capture());
        MajorPlannerResult stored = captor.getValue();
        assertThat(stored.getPlanCodeHash()).startsWith("$2").doesNotContain(view.getPlanCode());
        assertThat(stored.getPlanCodeFingerprint()).hasSize(64);
        assertThat(stored.getAnswersJson()).contains("物理 + 化学 + 政治");
    }

    @Test
    void evaluate_withRejectedMedicine_shouldDemoteMedicalDirections() {
        when(mapper.insert(any(MajorPlannerResult.class))).thenAnswer(invocation -> {
            MajorPlannerResult row = invocation.getArgument(0);
            row.setId(13L);
            return 1;
        });
        MajorPlannerService.EvaluateRequest req = request();
        req.setInterestDirections(List.of("医学", "稳定"));
        req.setAcceptMedicine(false);

        MajorPlannerService.MajorPlannerView view = service.evaluate(req);

        assertThat(view.getResult().getTopMajors())
                .extracting(MajorPlannerService.ScoredMajor::getCategory)
                .doesNotContain("临床医学类");
        assertThat(view.getResult().getNotRecommended())
                .anyMatch(item -> item.getDirection().contains("临床") || item.getDirection().contains("口腔"));
    }

    @Test
    void detail_withWrongPlanCode_shouldLockAfterFiveFailures() {
        String rightCode = "ABCD2345EFGH";
        MajorPlannerResult row = new MajorPlannerResult();
        row.setId(21L);
        row.setPlanNo("MP202606130021");
        row.setPlanCodeHash(codeService.hash(rightCode));
        when(mapper.selectById(anyLong())).thenReturn(row);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.detail(21L, "WRONG2345", "203.0.113.9"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("规划码错误");
        }

        assertThatThrownBy(() -> service.detail(21L, rightCode, "203.0.113.9"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尝试次数过多");
    }

    private MajorPlannerService.EvaluateRequest request() {
        MajorPlannerService.EvaluateRequest req = new MajorPlannerService.EvaluateRequest();
        req.setProvinceCode("GZ");
        req.setSubjectCategory("物理 + 化学 + 政治");
        req.setScore(600);
        req.setRank(8000);
        req.setLikedSubjects(List.of("数学", "英语", "物理", "信息技术"));
        req.setDislikedSubjects(List.of("历史"));
        req.setInterestDirections(List.of("技术", "财经", "研究型"));
        req.setPersonalityTraits(List.of("喜欢独立研究", "喜欢创新挑战"));
        req.setCareerExpectations(List.of("高薪", "考研"));
        req.setAcceptMedicine(true);
        req.setAcceptTeacher(true);
        req.setAcceptAgriculture(false);
        req.setFamilyBudget("均衡预算");
        req.setCityPreferences(List.of("贵阳", "成都"));
        req.setAvoidDirections(List.of("农林"));
        return req;
    }
}
