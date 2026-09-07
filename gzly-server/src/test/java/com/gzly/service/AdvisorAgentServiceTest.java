package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.compliance.ComplianceTextGuard;
import com.gzly.mapper.UniversityMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 工具层测试：工具执行的过滤、参数校验与标签生成。
 * LLM 循环本身依赖外部网关，通过工具层与事件协议的确定性保证质量。
 */
class AdvisorAgentServiceTest {

    private AdvisorAgentService newService(AlgorithmService algorithmService) {
        return newService(algorithmService, mock(SkillsRagService.class));
    }

    private AdvisorAgentService newService(AlgorithmService algorithmService, SkillsRagService skillsRagService) {
        return new AdvisorAgentService(
                mock(AiConfigService.class),
                mock(AiService.class),
                mock(VolunteerService.class),
                skillsRagService,
                algorithmService,
                mock(ScoreLineService.class),
                mock(UniversityMapper.class),
                new ObjectMapper(),
                mock(ComplianceTextGuard.class),
                mock(VolunteerMetricsRecorder.class));
    }

    private VolunteerService.PlanResult samplePlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(1L);
        plan.setFirstSubject("物理");
        List<VolunteerService.VolunteerItem> items = new ArrayList<>();
        items.add(item(1, "贵州大学", "计算机科学与技术", "稳", 72));
        items.add(item(2, "贵州师范大学", "数学与应用数学", "保", 88));
        items.add(item(3, "重庆大学", "土木工程", "冲", 45));
        plan.setItems(items);
        return plan;
    }

    private VolunteerService.VolunteerItem item(int index, String school, String major, String gradient, int chance) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(index);
        item.setUniversityName(school);
        item.setMajorName(major);
        item.setGradient(gradient);
        item.setChanceScore(chance);
        item.setHistoryMinRank(30000);
        item.setRiskLevel("中等");
        return item;
    }

    @Test
    void executeTool_getPlanItems_filtersByGradientAndKeyword() {
        AdvisorAgentService service = newService(mock(AlgorithmService.class));
        VolunteerService.PlanResult plan = samplePlan();

        String byGradient = service.executeTool("get_plan_items", "{\"gradient\":\"保\"}", plan, null);
        assertThat(byGradient).contains("贵州师范大学").doesNotContain("重庆大学");

        String byKeyword = service.executeTool("get_plan_items", "{\"keyword\":\"计算机\"}", plan, null);
        assertThat(byKeyword).contains("贵州大学").doesNotContain("贵州师范大学");
    }

    @Test
    void executeTool_queryRank_rejectsIllegalScore() {
        AdvisorAgentService service = newService(mock(AlgorithmService.class));
        String result = service.executeTool("query_rank", "{\"score\":9999}", samplePlan(), null);
        assertThat(result).contains("分数不合法");
    }

    @Test
    void executeTool_queryRank_usesPlanSubjectType() {
        AlgorithmService algorithmService = mock(AlgorithmService.class);
        AlgorithmService.RankEstimate estimate = new AlgorithmService.RankEstimate();
        estimate.setEstimatedRank(42000);
        estimate.setRankLow(41000);
        estimate.setRankHigh(43000);
        estimate.setReferenceYear(2025);
        when(algorithmService.estimateRank(anyInt(), anyString())).thenReturn(estimate);

        AdvisorAgentService service = newService(algorithmService);
        String result = service.executeTool("query_rank", "{\"score\":520}", samplePlan(), null);
        assertThat(result).contains("42000").contains("物理类");
    }

    @Test
    void executeTool_searchSkills_collectsSourcesIntoContext() {
        SkillsRagService skillsRagService = mock(SkillsRagService.class);
        SkillsRagService.RetrievedDigest digest = new SkillsRagService.RetrievedDigest();
        SkillsRagService.SourceChunk chunk = new SkillsRagService.SourceChunk();
        chunk.setText("冲稳保比例参考");
        digest.setDigest("片段1：冲稳保比例参考");
        digest.setChunks(List.of(chunk));
        when(skillsRagService.retrieveDigest(anyString())).thenReturn(digest);

        AdvisorAgentService service = newService(mock(AlgorithmService.class), skillsRagService);
        AdvisorAgentService.AgentRunContext context = new AdvisorAgentService.AgentRunContext("怎么排冲稳保");
        String result = service.executeTool("search_skills", "{\"query\":\"冲稳保\"}", samplePlan(), context);

        assertThat(result).contains("冲稳保比例参考");
        assertThat(context.sources).hasSize(1);
    }

    @Test
    void executeTool_unknownTool_returnsReadableError() {
        AdvisorAgentService service = newService(mock(AlgorithmService.class));
        String result = service.executeTool("no_such_tool", "{}", samplePlan(), null);
        assertThat(result).contains("未知工具");
    }

    @Test
    void executeTool_neverThrowsOnBrokenArgs() {
        AdvisorAgentService service = newService(mock(AlgorithmService.class));
        String result = service.executeTool("get_plan_items", "not-a-json", samplePlan(), null);
        assertThat(result).contains("工具执行失败");
    }

    @Test
    void toolLabel_readableForUsers() {
        AdvisorAgentService service = newService(mock(AlgorithmService.class));
        assertThat(service.toolLabel("query_rank", "{\"score\":520}")).isEqualTo("查询官方一分一段：520 分");
        assertThat(service.toolLabel("query_school_history", "{\"schoolName\":\"贵州大学\"}"))
                .isEqualTo("查询院校历年录取线：贵州大学");
        assertThat(service.toolLabel("search_skills", "{\"query\":\"就业优先\"}"))
                .isEqualTo("检索填报策略库：就业优先");
    }
}
