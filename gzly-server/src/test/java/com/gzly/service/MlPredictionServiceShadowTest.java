package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.mapper.PlanHistoryMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ML shadow 模式测试：shadow 只记录对比、绝不改写用户可见的规则输出；
 * mode 空值时保持旧 enabled 布尔开关语义。
 */
class MlPredictionServiceShadowTest {

    @Test
    void resolveMode_keepsLegacyEnabledSemantics() throws Exception {
        MlPredictionService service = newService();
        setField(service, "mode", "");
        setField(service, "enabled", false);
        assertThat(service.resolveMode()).isEqualTo("off");

        setField(service, "enabled", true);
        assertThat(service.resolveMode()).isEqualTo("active");

        setField(service, "mode", "shadow");
        assertThat(service.resolveMode()).isEqualTo("shadow");

        setField(service, "mode", "OFF");
        assertThat(service.resolveMode()).isEqualTo("off");
    }

    @Test
    void applyPredictions_shadowFailureNeverTouchesRuleOutputs() throws Exception {
        MlPredictionService service = newService();
        setField(service, "mode", "shadow");
        setField(service, "enabled", false);
        // 指向不可达端口：shadow 调用失败也不允许影响用户结果
        setField(service, "baseUrl", "http://127.0.0.1:1");
        Field timeout = MlPredictionService.class.getDeclaredField("timeoutMs");
        timeout.setAccessible(true);
        timeout.setInt(service, 200);

        VolunteerService.PlanResult plan = samplePlan();
        plan.getItems().get(0).setChanceScore(66);
        plan.getItems().get(0).setChanceLevel("适中");

        MlPredictionService.ApplyResult result = service.applyPredictions(null, plan, 96);

        assertThat(result.getMode()).isEqualTo("shadow");
        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).startsWith("shadow_failed");
        // 规则输出原样保留
        assertThat(plan.getItems().get(0).getChanceScore()).isEqualTo(66);
        assertThat(plan.getItems().get(0).getChanceLevel()).isEqualTo("适中");
        Map<String, Object> map = result.toMap();
        assertThat(map).containsEntry("mode", "shadow");
    }

    @Test
    void compareShadow_reportsDiffWithoutMutatingItems() throws Exception {
        MlPredictionService service = newService();
        VolunteerService.PlanResult plan = samplePlan();
        VolunteerService.VolunteerItem item = plan.getItems().get(0);
        item.setChanceScore(60);
        item.setPredictedMinRank(20_000);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(
                "{\"modelVersion\":\"chance-score-v-test\",\"predictions\":[" +
                        "{\"chanceScore\":80,\"predictedMinRank\":21000}]}");

        MlPredictionService.ShadowComparison comparison =
                service.compareShadow(plan.getItems(), root, plan.getProvinceRank());

        assertThat(comparison.comparedCount).isEqualTo(1);
        assertThat(comparison.chanceMaxAbsDiff).isEqualTo(20);
        assertThat(comparison.riskBandFlipCount).isEqualTo(1); // 60(中档) vs 80(高档)
        assertThat(comparison.rankComparedCount).isEqualTo(1);
        assertThat(comparison.rankMeanAbsDiff()).isEqualTo(1000);
        // item 不被改写
        assertThat(item.getChanceScore()).isEqualTo(60);
        assertThat(item.getPredictedMinRank()).isEqualTo(20_000);
        assertThat(comparison.toMap()).containsEntry("comparedCount", 1);
        assertThat(comparison.summaryLine()).contains("compared=1");
    }

    private MlPredictionService newService() {
        PlanHistoryMapper planHistoryMapper = mock(PlanHistoryMapper.class);
        when(planHistoryMapper.update(any(), any())).thenReturn(1);
        return new MlPredictionService(new ObjectMapper(), planHistoryMapper, new FallbackRulePredictionEngine());
    }

    private void setField(MlPredictionService service, String name, Object value) throws Exception {
        Field field = MlPredictionService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private VolunteerService.PlanResult samplePlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode("GZ");
        plan.setProvinceRank(18_000);
        plan.setTargetCount(96);
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setSchoolId("10001");
        item.setUniversityName("贵州大学");
        item.setMajorName("计算机科学与技术");
        item.setGradient("稳");
        plan.setItems(new ArrayList<>(List.of(item)));
        return plan;
    }
}
