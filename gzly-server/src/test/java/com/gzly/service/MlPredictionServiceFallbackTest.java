package com.gzly.service;

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
 * MlPredictionService 降级链路测试：验证 ML 关闭和 ML 失败两条降级路径都能正确填充 ApplyResult / chanceScore。
 */
class MlPredictionServiceFallbackTest {

    @Test
    void applyPredictions_returnsFallbackResultWhenMlDisabled() throws Exception {
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine();
        PlanHistoryMapper planHistoryMapper = mock(PlanHistoryMapper.class);
        when(planHistoryMapper.update(any(), any())).thenReturn(1);

        MlPredictionService service = new MlPredictionService(new ObjectMapper(), planHistoryMapper, engine, admissionYearService());
        setEnabled(service, false);

        VolunteerService.PlanResult plan = samplePlan();
        MlPredictionService.ApplyResult result = service.applyPredictions(null, plan, 96);

        assertThat(result.isModelEnabled()).isFalse();
        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isEqualTo("ml_disabled");
        Map<String, Object> map = result.toMap();
        assertThat(map).containsEntry("modelEnabled", false);
        assertThat(map).containsEntry("fallbackUsed", true);
    }

    @Test
    void applyPredictions_runsRuleFallbackWhenMlServiceUnreachable() throws Exception {
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine();
        PlanHistoryMapper planHistoryMapper = mock(PlanHistoryMapper.class);
        when(planHistoryMapper.update(any(), any())).thenReturn(1);

        MlPredictionService service = new MlPredictionService(new ObjectMapper(), planHistoryMapper, engine, admissionYearService());
        setEnabled(service, true);
        // 指向不可达的端口，触发 IOException → 降级
        Field baseUrl = MlPredictionService.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        baseUrl.set(service, "http://127.0.0.1:1");
        Field timeout = MlPredictionService.class.getDeclaredField("timeoutMs");
        timeout.setAccessible(true);
        timeout.setInt(service, 200);

        VolunteerService.PlanResult plan = samplePlan();
        // chanceScore=0 + predictedMinRank/historyMinRank 已设置，FallbackRulePredictionEngine 应该能赋出值
        for (VolunteerService.VolunteerItem item : plan.getItems()) {
            item.setChanceScore(0);
            item.setPredictedMinRank(20000);
            item.setHistoryMinRank(20000);
            item.setDataConfidence(70);
        }
        MlPredictionService.ApplyResult result = service.applyPredictions(null, plan, 96);

        assertThat(result.isModelEnabled()).isTrue();
        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isNotBlank();
        // ML 调用失败但 FallbackRulePredictionEngine 应当兜出非零 chanceScore
        long enriched = plan.getItems().stream().filter(i -> i.getChanceScore() > 0).count();
        assertThat(enriched).isGreaterThan(0);
    }

    private void setEnabled(MlPredictionService service, boolean value) throws Exception {
        Field field = MlPredictionService.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.setBoolean(service, value);
    }

    private AdmissionYearService admissionYearService() {
        AdmissionYearService service = new AdmissionYearService();
        service.setActiveAdmissionYear(2026);
        service.setHistoryYears("2025,2024");
        return service;
    }

    private VolunteerService.PlanResult samplePlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setProvinceCode("GZ");
        plan.setProvinceRank(18000);
        plan.setTargetCount(96);
        plan.setItems(new ArrayList<>(List.of(item("贵州大学", "计算机科学与技术", "稳"))));
        return plan;
    }

    private VolunteerService.VolunteerItem item(String school, String major, String gradient) {
        VolunteerService.VolunteerItem v = new VolunteerService.VolunteerItem();
        v.setSchoolId("10001");
        v.setUniversityName(school);
        v.setMajorName(major);
        v.setGradient(gradient);
        return v;
    }
}
