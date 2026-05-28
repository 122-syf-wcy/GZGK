package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PlanHistoryMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlPredictionService {

    private static final int MIN_DATA_CONFIDENCE_TO_APPLY = 35;
    private static final int MAX_PREDICTED_RANK_FACTOR = 3;
    private static final int MAX_RANK_SHIFT_ABSOLUTE = 80_000;
    private static final double MIN_APPLIED_RATIO = 0.60D;

    private final ObjectMapper objectMapper;
    private final PlanHistoryMapper planHistoryMapper;
    private final ProvinceAlgorithmPolicyService provinceAlgorithmPolicyService;

    @Value("${gzly.ml.enabled:false}")
    private boolean enabled;
    @Value("${gzly.ml.base-url:http://127.0.0.1:8091}")
    private String baseUrl;
    @Value("${gzly.ml.timeout-ms:1200}")
    private int timeoutMs;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(900))
            .build();

    public ApplyResult applyPredictions(VolunteerService.GenerateRequest req,
                                        VolunteerService.PlanResult plan,
                                        Integer policyMaxCount) {
        int maxCount = policyMaxCount == null || policyMaxCount <= 0 ? plan.getTargetCount() : policyMaxCount;
        applyPolicyLimit(plan, maxCount);

        ApplyResult result = new ApplyResult();
        result.setModelVersion("fallback-rule-v1");
        result.setFallbackUsed(true);
        result.setVisibleMetric("chanceScore");

        ProvinceAlgorithmPolicyService.AlgorithmPolicy algorithmPolicy = provinceAlgorithmPolicyService.resolve(
                plan == null ? (req == null ? null : req.getProvinceCode()) : plan.getProvinceCode(),
                policyConfig(req, plan));
        result.setPolicyVersion(algorithmPolicy.getPolicyVersion());
        result.setAlgorithmFamily(algorithmPolicy.getAlgorithmFamily());
        result.setGenerationEngine(algorithmPolicy.getGenerationEngine());
        result.setModelRoute(algorithmPolicy.getModelRoute());
        result.setModelRouteStatus(algorithmPolicy.getModelRouteStatus());
        result.setMlEligible(algorithmPolicy.isMlEligible());

        if (!enabled || plan == null || plan.getItems() == null || plan.getItems().isEmpty()) {
            persistPlanItems(plan);
            return result;
        }

        if (!algorithmPolicy.isMlEligible()) {
            result.setFallbackReason(algorithmPolicy.getModelRouteStatus() + ": " + algorithmPolicy.getMlModelPolicy());
            persistPlanItems(plan);
            return result;
        }

        int skipped = 0;
        List<String> qualityWarnings = new ArrayList<>();
        try {
            String payload = objectMapper.writeValueAsString(buildRequest(req, plan));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBaseUrl() + "/ml/predict/batch"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMillis(Math.max(500, timeoutMs)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ML 服务响应异常: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode predictions = root.path("predictions");
            if (!predictions.isArray()) {
                throw new IllegalStateException("ML 返回缺少 predictions");
            }
            List<VolunteerService.VolunteerItem> items = plan.getItems();
            int applied = 0;
            List<Integer> applyIndexes = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                JsonNode prediction = i < predictions.size() ? predictions.get(i) : null;
                PredictionQuality quality = predictionQuality(items.get(i), prediction, plan.getProvinceRank());
                if (!quality.isPass()) {
                    skipped++;
                    if (qualityWarnings.size() < 6) {
                        qualityWarnings.add("item#" + (i + 1) + ":" + quality.getReason());
                    }
                    continue;
                }
                applyIndexes.add(i);
            }
            applied = applyIndexes.size();
            if (applied == 0) {
                throw new IllegalStateException("ML 未返回可用预测");
            }
            int expected = items.size();
            if (expected > 1 && applied < Math.ceil(expected * MIN_APPLIED_RATIO)) {
                throw new IllegalStateException("ML 质量门禁未过: applied=" + applied + ", expected=" + expected
                        + ", skipped=" + skipped + ", warnings=" + qualityWarnings);
            }
            for (Integer i : applyIndexes) {
                applyPrediction(items.get(i), predictions.get(i), plan.getProvinceRank());
            }
            result.setModelVersion(root.path("modelVersion").asText("chance-score-v1.0.0"));
            result.setFallbackUsed(false);
            result.setAppliedCount(applied);
            result.setSkippedCount(skipped);
            result.setQualityGate("PASS");
            result.setQualityWarnings(qualityWarnings);
            persistPlanItems(plan);
            return result;
        } catch (Exception e) {
            log.warn("ML 机会指数服务不可用，已回退规则算法: {}", e.getMessage());
            result.setFallbackReason(e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage());
            result.setQualityGate("FAIL");
            result.setSkippedCount(skipped);
            result.setQualityWarnings(qualityWarnings);
            persistPlanItems(plan);
            return result;
        }
    }

    private PolicyRuleConfig policyConfig(VolunteerService.GenerateRequest req, VolunteerService.PlanResult plan) {
        PolicyRuleConfig config = new PolicyRuleConfig();
        config.setProvince(plan == null ? (req == null ? ProvincePolicyService.GZ : req.getProvinceCode()) : plan.getProvinceCode());
        config.setCandidateType(req == null || req.getCandidateType() == null ? "普通类" : req.getCandidateType());
        config.setBatchCode(req == null || req.getBatchCode() == null ? "NORMAL_UNDERGRADUATE" : req.getBatchCode());
        config.setBatchName(req == null ? null : req.getPolicyBatchName());
        config.setVolunteerMode(req == null ? null : req.getPolicyVolunteerUnitLabel());
        return config;
    }

    private void applyPolicyLimit(VolunteerService.PlanResult plan, int maxCount) {
        if (plan == null || plan.getItems() == null || maxCount <= 0) return;
        List<VolunteerService.VolunteerItem> items = plan.getItems();
        if (items.size() > maxCount) {
            items = new ArrayList<>(items.subList(0, maxCount));
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setIndex(i + 1);
            }
            plan.setItems(items);
        }
        plan.setTargetCount(maxCount);
    }

    private Map<String, Object> buildRequest(VolunteerService.GenerateRequest req, VolunteerService.PlanResult plan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("modelVersion", "latest");
        root.put("candidate", Map.of(
                "year", req == null || req.getYear() == null ? 2025 : req.getYear(),
                "province", plan.getProvinceCode(),
                "candidateRank", plan.getProvinceRank(),
                "candidateScore", plan.getTotalScore(),
                "subjectType", plan.getFirstSubject(),
                "batchCode", req == null ? "NORMAL_UNDERGRADUATE" : req.getBatchCode(),
                "riskPreference", plan.getStrategyMode() == null ? "均衡型" : plan.getStrategyMode()
        ));
        List<Map<String, Object>> items = new ArrayList<>();
        for (VolunteerService.VolunteerItem item : plan.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getSchoolId() + "_" + item.getMajorName() + "_" + item.getIndex());
            row.put("schoolCode", item.getSchoolId());
            row.put("schoolName", item.getUniversityName());
            row.put("majorName", item.getMajorName());
            row.put("majorCategory", item.getMajorName());
            row.put("schoolCity", item.getCity());
            row.put("schoolLevel", item.getTags() == null ? "" : String.join(",", item.getTags()));
            row.put("isPublic", !"民办".equals(item.getSchoolNature()));
            row.put("isChineseForeignCoop", contains(item.getMajorName(), "中外") || contains(item.getSchoolNature(), "中外"));
            row.put("planCount", item.getLatestPlanCount());
            row.put("currentPlanCount", item.getLatestPlanCount());
            row.put("historyMinRank", item.getHistoryMinRank());
            row.put("predictedMinRank", item.getPredictedMinRank());
            row.put("rankDiff", item.getRankDiff());
            row.put("rankVolatility3y", Math.abs(item.getRankGap()));
            row.put("planChangeRate", planTrendScore(item.getPlanTrend()));
            row.put("schoolHotScore", item.getSchoolEnrollmentIndex());
            row.put("majorHotScore", item.getPlanExpansionIndex());
            row.put("dataMissingCount", "专业级".equals(item.getDataSourceType()) ? 0 : 1);
            row.put("tuition", 0);
            row.put("firstRoundFullLag1", "缩招".equals(item.getPlanTrend()) ? 1 : 0);
            row.put("hasSupplementLag1", item.isNeedsManualReview() ? 1 : 0);
            items.add(row);
        }
        root.put("items", items);
        return root;
    }

    private double planTrendScore(String trend) {
        if ("扩招".equals(trend)) return 0.12;
        if ("缩招".equals(trend)) return -0.12;
        return 0.0;
    }

    private void applyPrediction(VolunteerService.VolunteerItem item, JsonNode prediction, int candidateRank) {
        int predicted = prediction.path("predictedMinRank").asInt(item.getPredictedMinRank() > 0 ? item.getPredictedMinRank() : item.getHistoryMinRank());
        int chance = prediction.path("chanceScore").asInt(item.getChanceScore());
        item.setPredictedMinRank(Math.max(0, predicted));
        if (predicted > 0 && candidateRank > 0) {
            item.setRankDiff(predicted - candidateRank);
        }
        item.setChanceScore(Math.max(0, Math.min(100, chance)));
        item.setChanceLevel(blankToDefault(prediction.path("chanceLevel").asText(null), item.getChanceLevel()));
        item.setRiskLevel(blankToDefault(prediction.path("riskLevel").asText(null), item.getRiskLevel()));
        item.setConfidenceLevel(blankToDefault(prediction.path("confidenceLevel").asText(null), item.getConfidenceLevel()));
        double dataConfidence = prediction.path("dataConfidence").asDouble(item.getDataConfidence());
        if (dataConfidence > 0 && dataConfidence <= 1.0D) {
            dataConfidence *= 100D;
        }
        item.setDataConfidence(Math.max(0, Math.min(100, dataConfidence)));
        item.setDataConfidenceScore((int) Math.round(item.getDataConfidence()));
        if (item.getChanceScore() >= 75) item.setRiskColor("green");
        else if (item.getChanceScore() >= 50) item.setRiskColor("yellow");
        else item.setRiskColor("red");
    }

    private PredictionQuality predictionQuality(VolunteerService.VolunteerItem item, JsonNode prediction, int candidateRank) {
        PredictionQuality quality = new PredictionQuality();
        if (item == null || prediction == null || prediction.isMissingNode() || !prediction.isObject()) {
            quality.setReason("missing_prediction");
            return quality;
        }
        int fallbackPredicted = item.getPredictedMinRank() > 0 ? item.getPredictedMinRank() : item.getHistoryMinRank();
        int predicted = prediction.path("predictedMinRank").asInt(fallbackPredicted);
        int chance = prediction.path("chanceScore").asInt(item.getChanceScore());
        double confidence = normalizedConfidence(prediction.path("dataConfidence").asDouble(item.getDataConfidence()));
        if (predicted <= 0) {
            quality.setReason("invalid_predicted_rank");
            return quality;
        }
        int maxReasonableRank = Math.max(200_000, Math.max(candidateRank, fallbackPredicted) * MAX_PREDICTED_RANK_FACTOR);
        if (predicted > maxReasonableRank) {
            quality.setReason("predicted_rank_outlier");
            return quality;
        }
        int anchor = fallbackPredicted > 0 ? fallbackPredicted : candidateRank;
        if (anchor > 0 && Math.abs(predicted - anchor) > MAX_RANK_SHIFT_ABSOLUTE) {
            quality.setReason("rank_shift_outlier");
            return quality;
        }
        if (chance < 0 || chance > 100) {
            quality.setReason("invalid_chance_score");
            return quality;
        }
        if (confidence < MIN_DATA_CONFIDENCE_TO_APPLY) {
            quality.setReason("low_data_confidence");
            return quality;
        }
        if (candidateRank > 0) {
            int rankDiff = predicted - candidateRank;
            if (rankDiff >= 20_000 && chance < 35) {
                quality.setReason("chance_rank_inconsistent_safe");
                return quality;
            }
            if (rankDiff <= -20_000 && chance > 75) {
                quality.setReason("chance_rank_inconsistent_risky");
                return quality;
            }
        }
        quality.setPass(true);
        return quality;
    }

    private double normalizedConfidence(double value) {
        double confidence = value > 0 && value <= 1.0D ? value * 100D : value;
        return Math.max(0D, Math.min(100D, confidence));
    }

    private void persistPlanItems(VolunteerService.PlanResult plan) {
        if (plan == null || plan.getId() <= 0 || plan.getItems() == null) return;
        try {
            planHistoryMapper.update(null, new UpdateWrapper<PlanHistory>()
                    .eq("id", plan.getId())
                    .set("plan_json", objectMapper.writeValueAsString(plan.getItems()))
                    .set("item_count", plan.getItems().size()));
        } catch (Exception e) {
            log.warn("回写 ML 志愿项失败: planId={}, reason={}", plan.getId(), e.getMessage());
        }
    }

    private String resolveBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8091" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean contains(String text, String keyword) {
        return text != null && keyword != null && text.contains(keyword);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Data
    public static class ApplyResult {
        private String modelVersion;
        private boolean fallbackUsed;
        private String fallbackReason;
        private String visibleMetric;
        private int appliedCount;
        private String policyVersion;
        private String algorithmFamily;
        private String generationEngine;
        private String modelRoute;
        private String modelRouteStatus;
        private boolean mlEligible;
        private String qualityGate = "NOT_RUN";
        private int skippedCount;
        private List<String> qualityWarnings = List.of();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("modelVersion", modelVersion);
            map.put("fallbackUsed", fallbackUsed);
            map.put("visibleMetric", visibleMetric);
            map.put("appliedCount", appliedCount);
            map.put("policyVersion", policyVersion);
            map.put("algorithmFamily", algorithmFamily);
            map.put("generationEngine", generationEngine);
            map.put("modelRoute", modelRoute);
            map.put("modelRouteStatus", modelRouteStatus);
            map.put("mlEligible", mlEligible);
            map.put("qualityGate", qualityGate);
            map.put("skippedCount", skippedCount);
            map.put("qualityWarnings", qualityWarnings == null ? List.of() : qualityWarnings);
            if (fallbackReason != null && !fallbackReason.isBlank()) {
                map.put("fallbackReason", fallbackReason);
            }
            return map;
        }
    }

    @Data
    private static class PredictionQuality {
        private boolean pass;
        private String reason;
    }
}
