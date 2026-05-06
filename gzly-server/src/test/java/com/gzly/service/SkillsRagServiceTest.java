package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.entity.SkillsChunk;
import com.gzly.entity.SkillsQueryLog;
import com.gzly.mapper.SkillsChunkMapper;
import com.gzly.mapper.SkillsQueryLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillsRagService 行为测试。
 *
 * <ul>
 *   <li>缺 plan / 缺问题 → 抛业务异常。</li>
 *   <li>命中 chunks → 调用 AiService.chatWithAdvisorSkill 并 sanitize 后持久化。</li>
 *   <li>无 chunks → 走兜底文案，不再调 AI。</li>
 *   <li>返回结构 6 字段全填齐：answer/actionItems/referencedVolunteers/sourceChunks/riskWarnings/disclaimer。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillsRagServiceTest {

    @Mock VolunteerService volunteerService;
    @Mock SkillsChunkMapper chunkMapper;
    @Mock SkillsQueryLogMapper queryLogMapper;
    @Mock AiService aiService;
    @Mock ComplianceTextGuard complianceTextGuard;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SkillsRagService service;

    @BeforeEach
    void setUp() {
        // sanitizeText 默认透传以便断言原文
        when(complianceTextGuard.sanitizeText(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2, String.class));
    }

    @Test
    void ask_throwsWhenAccessKeyMissing() {
        assertThatThrownBy(() -> service.ask(101L, "", "学校怎么选", null, null))
                .hasMessageContaining("方案参数");
    }

    @Test
    void ask_throwsWhenQuestionTooShort() {
        assertThatThrownBy(() -> service.ask(101L, "key", "?", null, null))
                .hasMessageContaining("咨询的问题");
    }

    @Test
    void ask_returnsFallbackAnswer_whenNoChunksMatched() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(101L, "key")).thenReturn(plan);
        // 关键词检索 + 兜底回退检索都返回空
        when(chunkMapper.selectList(any())).thenReturn(List.of());

        SkillsRagService.SkillsAnswer answer = service.ask(101L, "key", "学校该怎么选？", null, null);

        assertThat(answer.getAnswer()).contains("当前策略库没有足够依据");
        assertThat(answer.getActionItems()).isNotEmpty();
        assertThat(answer.getReferencedVolunteers()).isNotEmpty();
        assertThat(answer.getSourceChunks()).isEmpty();
        assertThat(answer.getRiskWarnings()).isNotEmpty();
        assertThat(answer.getDisclaimer()).contains("仅供参考");
        // 兜底路径下不能再调 AI
        verify(aiService, never()).chatWithAdvisorSkill(anyString(), anyString(), anyString(), anyString(), any());
        verify(queryLogMapper).insert(any(SkillsQueryLog.class));
        verify(complianceTextGuard, atLeastOnce()).sanitizeText(eq("skills_qa"), anyString(), anyString());
    }

    @Test
    void ask_callsAiAndSanitizes_whenChunksMatched() {
        VolunteerService.PlanResult plan = samplePlan();
        when(volunteerService.getPlanResult(101L, "key")).thenReturn(plan);
        // 关键词命中：返回 2 条 chunk（"专业" 关键词匹配）
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(7L, "专业选择策略要看就业方向"),
                chunk(8L, "城市优先：一线城市的就业机会密度更高")));
        when(aiService.chatWithAdvisorSkill(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("> 本内容由 AI 生成，仅供参考。\n\n建议先看就业、再看城市。");

        SkillsRagService.SkillsAnswer answer = service.ask(101L, "key", "我要按就业优先选专业，怎么取舍？", null, null);

        assertThat(answer.getAnswer()).contains("建议先看就业");
        assertThat(answer.getSourceChunks()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(answer.getActionItems()).isNotEmpty();
        assertThat(answer.getReferencedVolunteers()).isNotEmpty();
        assertThat(answer.getRiskWarnings()).isNotEmpty();
        // sanitize 必须穿透到答案
        verify(complianceTextGuard, atLeastOnce()).sanitizeText(eq("skills_qa"), anyString(), anyString());
        verify(aiService, times(1)).chatWithAdvisorSkill(anyString(), anyString(), anyString(), anyString(), any());
        // 持久化
        verify(queryLogMapper).insert(any(SkillsQueryLog.class));
    }

    @Test
    void suggestedQuestions_returnsNonEmptyList() {
        List<String> questions = service.suggestedQuestions();
        assertThat(questions).isNotEmpty();
        assertThat(questions).allSatisfy(q -> assertThat(q).isNotBlank());
    }

    private SkillsChunk chunk(long id, String text) {
        SkillsChunk row = new SkillsChunk();
        row.setId(id);
        row.setDocumentId(1L);
        row.setChunkIndex((int) id);
        row.setChunkText(text);
        row.setTagsJson("[\"专业选择\",\"就业优先\"]");
        row.setEmbeddingVector("[]");
        row.setTokenCount(text.length() / 2);
        return row;
    }

    private VolunteerService.PlanResult samplePlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(101L);
        plan.setProvinceCode("GZ");
        plan.setProvinceRank(21000);
        plan.setTotalScore(602);
        plan.setFirstSubject("物理");
        plan.setStrategyMode("均衡型");
        plan.setItems(new ArrayList<>(List.of(item(1, "冲", "贵州大学", "计算机类"),
                item(2, "稳", "贵州大学", "数学类"))));
        return plan;
    }

    private VolunteerService.VolunteerItem item(int index, String gradient, String university, String major) {
        VolunteerService.VolunteerItem it = new VolunteerService.VolunteerItem();
        it.setIndex(index);
        it.setGradient(gradient);
        it.setUniversityName(university);
        it.setMajorName(major);
        it.setCity("贵阳");
        it.setChanceScore(70);
        it.setChanceLevel("适中");
        it.setRiskLevel("中等");
        it.setDataConfidence(75);
        it.setDataConfidenceScore(75);
        it.setRecommendReason("近三年位次稳定");
        it.setRiskReason("");
        return it;
    }
}
