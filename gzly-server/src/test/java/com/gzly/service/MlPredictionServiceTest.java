package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MlPredictionServiceTest {

    @Test
    void shouldUseMlPredictionWhenServiceAvailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ml/predict/batch", exchange -> {
            byte[] bytes = """
                    {
                      "modelVersion": "chance-score-v1.0.0",
                      "predictions": [
                        {
                          "predictedMinRank": 24000,
                          "chanceScore": 76,
                          "chanceLevel": "稳妥参考",
                          "riskLevel": "较低",
                          "confidenceLevel": "高",
                          "dataConfidence": 0.86
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            MlPredictionService service = service("http://127.0.0.1:" + server.getAddress().getPort(), true, 1200);
            VolunteerService.PlanResult plan = oneItemPlan();

            MlPredictionService.ApplyResult result = service.applyPredictions(request(), plan, 96);

            assertThat(result.isFallbackUsed()).isFalse();
            assertThat(result.getModelVersion()).isEqualTo("chance-score-v1.0.0");
            assertThat(plan.getItems().get(0).getPredictedMinRank()).isEqualTo(24000);
            assertThat(plan.getItems().get(0).getRankDiff()).isEqualTo(3000);
            assertThat(plan.getItems().get(0).getChanceScore()).isEqualTo(76);
            assertThat(plan.getItems().get(0).getChanceLevel()).isEqualTo("稳妥参考");
            assertThat(plan.getItems().get(0).getRiskLevel()).isEqualTo("较低");
            assertThat(plan.getItems().get(0).getDataConfidenceScore()).isEqualTo(86);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallbackAndStillApplyPolicyLimitWhenMlDisabled() {
        MlPredictionService service = service("http://127.0.0.1:1", false, 1200);
        VolunteerService.PlanResult plan = oneItemPlan();
        plan.setItems(new ArrayList<>(List.of(item(1), item(2), item(3))));

        MlPredictionService.ApplyResult result = service.applyPredictions(request(), plan, 2);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getTargetCount()).isEqualTo(2);
        assertThat(plan.getItems()).extracting(VolunteerService.VolunteerItem::getIndex)
                .containsExactly(1, 2);
    }

    @Test
    void shouldFallbackWhenMlTimeoutOrUnavailable() {
        MlPredictionService service = service("http://127.0.0.1:1", true, 500);
        VolunteerService.PlanResult plan = oneItemPlan();

        MlPredictionService.ApplyResult result = service.applyPredictions(request(), plan, 96);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isNotBlank();
        assertThat(plan.getItems().get(0).getChanceScore()).isEqualTo(58);
    }

    private MlPredictionService service(String baseUrl, boolean enabled, int timeoutMs) {
        PlanHistoryMapper planHistoryMapper = mock(PlanHistoryMapper.class);
        when(planHistoryMapper.update(any(), any())).thenReturn(1);
        MlPredictionService service = new MlPredictionService(new ObjectMapper(), planHistoryMapper,
                new com.gzly.algorithm.FallbackRulePredictionEngine(), admissionYearService());
        ReflectionTestUtils.setField(service, "enabled", enabled);
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(service, "timeoutMs", timeoutMs);
        return service;
    }

    private AdmissionYearService admissionYearService() {
        AdmissionYearService service = new AdmissionYearService();
        service.setActiveAdmissionYear(2026);
        service.setHistoryYears("2025,2024");
        return service;
    }

    private VolunteerService.GenerateRequest request() {
        VolunteerService.GenerateRequest req = new VolunteerService.GenerateRequest();
        req.setYear(2025);
        req.setBatchCode("NORMAL_UNDERGRADUATE");
        req.setProvinceRank(21000);
        req.setTotalScore(602);
        req.setStrategyMode("均衡型");
        return req;
    }

    private VolunteerService.PlanResult oneItemPlan() {
        VolunteerService.PlanResult plan = new VolunteerService.PlanResult();
        plan.setId(88L);
        plan.setProvinceCode("GZ");
        plan.setProvinceRank(21000);
        plan.setTotalScore(602);
        plan.setFirstSubject("物理");
        plan.setStrategyMode("均衡型");
        plan.setTargetCount(96);
        plan.setItems(new ArrayList<>(List.of(item(1))));
        return plan;
    }

    private VolunteerService.VolunteerItem item(int index) {
        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(index);
        item.setSchoolId("10657");
        item.setUniversityName("贵州大学");
        item.setMajorName("计算机类");
        item.setCity("贵阳");
        item.setTags(List.of("双一流", "公办"));
        item.setHistoryMinRank(23000 + index);
        item.setPredictedMinRank(23000 + index);
        item.setChanceScore(58);
        item.setChanceLevel("适中");
        item.setRiskLevel("中等");
        item.setConfidenceLevel("中");
        item.setDataConfidence(64);
        item.setDataConfidenceScore(64);
        item.setRankGap(2000);
        item.setPlanTrend("基本稳定");
        item.setRiskColor("yellow");
        return item;
    }
}
