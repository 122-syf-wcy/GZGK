package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.VolunteerAiAnalysis;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.mapper.VolunteerAiAnalysisMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiDeepAnalysisService 行为测试。
 *
 * <ul>
 *   <li>AI 不可用 → fallback 路径填齐 aiFallbackUsed/aiModelVersion/aiFallbackReason/dataIssues。</li>
 *   <li>AI 返回有效 JSON → fallback=false 且模型名取自 AiService.currentChatModel。</li>
 *   <li>低参考度 / 需复核志愿被正确收集到 dataIssues。</li>
 *   <li>sanitize 调用穿透到 conclusion / sections / actions / topKeep / topRisk / reorderAdvice。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDeepAnalysisServiceTest {

    @Mock VolunteerService volunteerService;
    @Mock VolunteerAiAnalysisMapper analysisMapper;
    @Mock PlanHistoryMapper planHistoryMapper;
    @Mock ComplianceTextGuard complianceTextGuard;
    @Mock AiService aiService;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks AiDeepAnalysisService service;

    @BeforeEach
    void setUp() {
        // sanitizeText 默认透传，便于断言原文；真实合规审查由独立测试覆盖
        when(complianceTextGuard.sanitizeText(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2, String.class));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
    }

    @Test
    void fallbackPath_whenAiReturnsBlank_fillsFallbackMetadata() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(plan.getId(), null)).thenReturn(plan);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(aiService.generateStructuredAnalysisJson(anyString())).thenReturn("");

        AiDeepAnalysisService.AiAnalysisVO vo = service.generate(plan.getId(), null, true);

        assertThat(vo.getAiFallbackUsed()).isTrue();
        assertThat(vo.getAiModelVersion()).isEqualTo("rule-template-v1");
        assertThat(vo.getAiFallbackReason()).isNotBlank();
        assertThat(vo.getStatus()).isEqualTo("completed");
        assertThat(vo.getProgress()).isEqualTo(100);
        assertThat(vo.getDataIssues()).isNotEmpty();
        assertThat(vo.getDataIssues()).anySatisfy(t -> assertThat(t).contains("数据参考度"));
        assertThat(vo.getDisclaimer()).isNotBlank();
        // sanitize 必须穿透
        verify(complianceTextGuard, atLeastOnce()).sanitizeText(anyString(), anyString(), anyString());
        // 持久化路径
        verify(analysisMapper).insert(any(VolunteerAiAnalysis.class));
    }

    @Test
    void aiSuccessPath_setsModelVersionFromAiService() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(plan.getId(), null)).thenReturn(plan);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        String aiJson = """
                {
                  "conclusion": "本方案的稳妥参考与兜底参考分布合理，建议先核对前 5 条。",
                  "gradientSummary": {"rush": 1, "stable": 1, "safe": 1, "floor": 0},
                  "bestKeepItem": {"universityName": "贵州大学", "majorName": "计算机类"},
                  "highestRiskItem": {"universityName": "贵州大学", "majorName": "数学类"},
                  "diagnosisSections": [{"title": "梯度", "content": "整体梯度合理。"}],
                  "topKeepDirections": ["贵州大学 · 计算机类"],
                  "topRiskPoints": ["贵州大学 · 数学类 需要复核章程"],
                  "actionSteps": [{"title": "第一步", "content": "复核稳妥参考章程。"}],
                  "reorderAdvice": ["前段放置稳妥参考"],
                  "disclaimer": "本结果仅供辅助参考"
                }
                """;
        when(aiService.generateStructuredAnalysisJson(anyString())).thenReturn(aiJson);
        when(aiService.currentChatModel()).thenReturn("gpt-4o-mini");

        AiDeepAnalysisService.AiAnalysisVO vo = service.generate(plan.getId(), null, true);

        assertThat(vo.getAiFallbackUsed()).isFalse();
        assertThat(vo.getAiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(vo.getConclusion()).contains("稳妥参考");
        assertThat(vo.getDiagnosisSections()).isNotEmpty();
        assertThat(vo.getActionSteps()).isNotEmpty();
        // 即使 AI 成功，dataIssues 仍然要根据原始 plan 数据填充
        assertThat(vo.getDataIssues()).isNotEmpty();
        assertThat(vo.getDisclaimer()).isNotBlank();
        // sanitize 穿透到所有字段
        verify(complianceTextGuard, atLeastOnce()).sanitizeText(anyString(), anyString(), anyString());
    }

    @Test
    void cachedCompletedAnalysis_isReturnedDirectly_whenForceRefreshFalse() throws Exception {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(plan.getId(), null)).thenReturn(plan);
        AiDeepAnalysisService.AiAnalysisVO cached = new AiDeepAnalysisService.AiAnalysisVO();
        cached.setStatus("completed");
        cached.setConclusion("缓存的解读结论。");
        cached.setProgress(100);
        VolunteerAiAnalysis row = new VolunteerAiAnalysis();
        row.setSanitizedAnalysisText(objectMapper.writeValueAsString(cached));
        when(analysisMapper.selectOne(any())).thenReturn(row);

        AiDeepAnalysisService.AiAnalysisVO vo = service.generate(plan.getId(), null, false);

        assertThat(vo.getStatus()).isEqualTo("completed");
        assertThat(vo.getConclusion()).isEqualTo("缓存的解读结论。");
        // 不应当再次走 AI 调用
        verify(aiService, org.mockito.Mockito.never()).generateStructuredAnalysisJson(anyString());
    }

    @Test
    void generate_shouldRejectConcurrentStructuredAnalysisForSamePlan() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(plan.getId(), null)).thenReturn(plan);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.FALSE);

        assertThatThrownBy(() -> service.generate(plan.getId(), null, true))
                .isInstanceOf(com.gzly.common.exception.BizException.class)
                .hasMessageContaining("正在生成中");
    }

    @Test
    void generate_shouldReleaseStructuredAnalysisLockByOwnerToken() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(plan.getId(), null)).thenReturn(plan);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(aiService.generateStructuredAnalysisJson(anyString())).thenReturn("");
        AtomicReference<String> lockToken = new AtomicReference<>();
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            lockToken.set(inv.getArgument(1, String.class));
            return Boolean.TRUE;
        });

        AiDeepAnalysisService.AiAnalysisVO vo = service.generate(plan.getId(), null, true);

        assertThat(vo.getStatus()).isEqualTo("completed");
        assertThat(lockToken.get()).isNotBlank();
        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), eq(lockToken.get()));
    }

    private VolunteerService.PlanResult samplePlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(101L);
        plan.setProvinceCode("GZ");
        plan.setProvinceRank(21000);
        plan.setTotalScore(602);
        plan.setFirstSubject("物理");
        plan.setStrategyMode("均衡型");
        plan.setTargetCount(96);
        plan.setItems(new ArrayList<>(List.of(
                volunteerItem(1, "冲", 78, 86, "专业级", false),
                volunteerItem(2, "稳", 70, 50, "院校级", true), // 低参考度 + 需复核 + 院校级
                volunteerItem(3, "保", 65, 70, "专业级", false))));
        return plan;
    }

    private VolunteerService.VolunteerItem volunteerItem(int index, String gradient, int chance,
                                                          int dataConfidence, String dataSourceType,
                                                          boolean needsReview) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(index);
        item.setGradient(gradient);
        item.setUniversityName("贵州大学");
        item.setMajorName(index == 1 ? "计算机类" : index == 2 ? "数学类" : "物理学类");
        item.setCity("贵阳");
        item.setSchoolNature("公办");
        item.setChanceScore(chance);
        item.setChanceLevel(chance >= 75 ? "稳妥参考" : chance >= 50 ? "适中" : "冲刺参考");
        item.setRiskLevel(chance >= 75 ? "较低" : chance >= 50 ? "中等" : "较高");
        item.setConfidenceLevel(dataConfidence >= 70 ? "高" : "中");
        item.setDataConfidence(dataConfidence);
        item.setDataConfidenceScore(dataConfidence);
        item.setDataSourceType(dataSourceType);
        item.setNeedsManualReview(needsReview);
        item.setLatestPlanCount(15);
        item.setHistoryMinRank(23000);
        item.setPredictedMinRank(23000);
        return item;
    }
}
