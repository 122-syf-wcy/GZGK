package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.entity.PlanHistory;
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

    private final ObjectMapper objectMapper;
    private final PlanHistoryMapper planHistoryMapper;
    private final FallbackRulePredictionEngine fallbackRulePredictionEngine;

    @Value("${gzly.ml.enabled:false}")
    private boolean enabled;
    /** off / shadow / active；空值时按 enabled 推导（true=active, false=off），保持旧配置兼容。 */
    @Value("${gzly.ml.mode:}")
    private String mode;
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

        String effectiveMode = resolveMode();

        ApplyResult result = new ApplyResult();
        result.setModelVersion("fallback-rule-v1");
        result.setFallbackUsed(true);
        result.setModelEnabled(!"off".equals(effectiveMode));
        result.setMode(effectiveMode);
        result.setVisibleMetric("chanceScore");

        if ("off".equals(effectiveMode)) {
            // ML 关闭：已由 VolunteerService 主链路用 FallbackRulePredictionEngine 赋值，这里不重复
            result.setFallbackReason("ml_disabled");
            persistPlanItems(plan);
            return result;
        }
        if (plan == null || plan.getItems() == null || plan.getItems().isEmpty()) {
            result.setFallbackReason("empty_plan");
            persistPlanItems(plan);
            return result;
        }

        if ("shadow".equals(effectiveMode)) {
            // shadow：调用 ML 并记录与规则输出的对比，但用户结果保持规则引擎输出不变。
            try {
                JsonNode root = callMlPredictions(req, plan);
                ShadowComparison comparison = compareShadow(plan.getItems(), root, plan.getProvinceRank());
                result.setModelVersion(root.path("modelVersion").asText("chance-score-shadow"));
                result.setFallbackReason("shadow_mode");
                result.setShadowStats(comparison.toMap());
                log.info("ML shadow 对比: planId={}, {}", plan.getId(), comparison.summaryLine());
            } catch (Exception e) {
                log.warn("ML shadow 调用失败（不影响用户结果）: {}", e.getMessage());
                result.setFallbackReason("shadow_failed: " + (e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage()));
            }
            persistPlanItems(plan);
            return result;
        }

        try {
            JsonNode root = callMlPredictions(req, plan);
            JsonNode predictions = root.path("predictions");
            List<VolunteerService.VolunteerItem> items = plan.getItems();
            int applied = 0;
            for (int i = 0; i < predictions.size() && i < items.size(); i++) {
                applyPrediction(items.get(i), predictions.get(i), plan.getProvinceRank());
                applied++;
            }
            if (applied == 0) {
                throw new IllegalStateException("ML 未返回可用预测");
            }
            result.setModelVersion(root.path("modelVersion").asText("chance-score-v1.0.0"));
            result.setFallbackUsed(false);
            result.setAppliedCount(applied);
            persistPlanItems(plan);
            return result;
        } catch (Exception e) {
            log.warn("ML 机会指数服务不可用，已回退规则算法: {}", e.getMessage());
            result.setFallbackReason(e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage());
            // ML 调用失败：走 FallbackRulePredictionEngine 兑现购买保证（主链路可能已赋值，这里加一道兑现保证）
            int rescued = applyRuleFallback(plan);
            result.setAppliedCount(rescued);
            persistPlanItems(plan);
            return result;
        }
    }

    /** 显式 mode 优先；空值时兼容旧 enabled 布尔开关。 */
    String resolveMode() {
        String value = mode == null ? "" : mode.trim().toLowerCase();
        return switch (value) {
            case "off", "shadow", "active" -> value;
            default -> enabled ? "active" : "off";
        };
    }

    private JsonNode callMlPredictions(VolunteerService.GenerateRequest req,
                                       VolunteerService.PlanResult plan) throws Exception {
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
        if (!root.path("predictions").isArray()) {
            throw new IllegalStateException("ML 返回缺少 predictions");
        }
        return root;
    }

    /** 只统计差异，不写回任何 item 字段。包级可见以便单测。 */
    ShadowComparison compareShadow(List<VolunteerService.VolunteerItem> items,
                                   JsonNode root, int candidateRank) {
        JsonNode predictions = root.path("predictions");
        ShadowComparison comparison = new ShadowComparison();
        for (int i = 0; i < predictions.size() && i < items.size(); i++) {
            VolunteerService.VolunteerItem item = items.get(i);
            JsonNode prediction = predictions.get(i);
            int mlChance = Math.max(0, Math.min(100, prediction.path("chanceScore").asInt(item.getChanceScore())));
            int ruleChance = item.getChanceScore();
            comparison.comparedCount++;
            int diff = Math.abs(mlChance - ruleChance);
            comparison.chanceAbsDiffSum += diff;
            comparison.chanceMaxAbsDiff = Math.max(comparison.chanceMaxAbsDiff, diff);
            if (riskBand(mlChance) != riskBand(ruleChance)) {
                comparison.riskBandFlipCount++;
            }
            int mlPredicted = prediction.path("predictedMinRank").asInt(0);
            if (mlPredicted > 0 && item.getPredictedMinRank() > 0) {
                comparison.rankAbsDiffSum += Math.abs(mlPredicted - item.getPredictedMinRank());
                comparison.rankComparedCount++;
            }
        }
        return comparison;
    }

    private int riskBand(int chanceScore) {
        if (chanceScore >= 75) return 2;
        if (chanceScore >= 50) return 1;
        return 0;
    }

    /**
     * 当 ML 服务在运行期崩溃、主链路未调用过 FallbackRulePredictionEngine 时，
     * 这里补一道规则预测，避免走择出全部 chanceScore=0 的示警。2026/05 后主链路已不依赖本路径，
     * 但保留作为运维购买保证。
     */
    private int applyRuleFallback(VolunteerService.PlanResult plan) {
        if (plan == null || plan.getItems() == null || plan.getItems().isEmpty()) return 0;
        if (fallbackRulePredictionEngine == null) return 0;
        int candidateRank = Math.max(1, plan.getProvinceRank());
        int rescued = 0;
        for (VolunteerService.VolunteerItem item : plan.getItems()) {
            if (item.getChanceScore() > 0) continue; // 主链路已赋值不重复
            int referenceRank = item.getPredictedMinRank() > 0 ? item.getPredictedMinRank() : item.getHistoryMinRank();
            FallbackRulePredictionEngine.Prediction p = fallbackRulePredictionEngine.predict(
                    candidateRank,
                    referenceRank,
                    item.getRankVolatility3y(),
                    item.getPlanChangeRate(),
                    item.getDataConfidence(),
                    item.getHotTrendScore());
            item.setChanceScore(p.getChanceScore());
            item.setChanceLevel(p.getChanceLevel());
            item.setRiskLevel(p.getRiskLevel());
            item.setConfidenceLevel(p.getConfidenceLevel());
            item.setDataConfidence(p.getDataConfidence());
            item.setPredictedMinRank(p.getPredictedMinRank());
            item.setRankDiff(p.getRankDiff());
            rescued++;
        }
        return rescued;
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
            // 优先用 FeatureBuildEngine 写回的特征值；早期主链路未触达时回退到 abs(rankGap) / planTrendScore
            row.put("rankVolatility3y", item.getRankVolatility3y() > 0
                    ? item.getRankVolatility3y()
                    : Math.abs(item.getRankGap()) * 0.001D);
            row.put("planChangeRate", item.getPlanChangeRate() != 0
                    ? item.getPlanChangeRate()
                    : planTrendScore(item.getPlanTrend()));
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
        /** 配置上 ML 是否开启；false 时 fallbackUsed 一定为 true。 */
        private boolean modelEnabled;
        /** off / shadow / active。 */
        private String mode;
        /** shadow 模式下的规则 vs 模型对比统计。 */
        private Map<String, Object> shadowStats;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("modelVersion", modelVersion);
            map.put("modelEnabled", modelEnabled);
            map.put("fallbackUsed", fallbackUsed);
            map.put("visibleMetric", visibleMetric);
            map.put("appliedCount", appliedCount);
            if (mode != null && !mode.isBlank()) {
                map.put("mode", mode);
            }
            if (fallbackReason != null && !fallbackReason.isBlank()) {
                map.put("fallbackReason", fallbackReason);
            }
            if (shadowStats != null && !shadowStats.isEmpty()) {
                map.put("shadowStats", shadowStats);
            }
            return map;
        }
    }

    /** shadow 模式下"规则输出 vs 模型输出"的差异统计。 */
    static class ShadowComparison {
        int comparedCount;
        long chanceAbsDiffSum;
        int chanceMaxAbsDiff;
        int riskBandFlipCount;
        long rankAbsDiffSum;
        int rankComparedCount;

        double chanceMeanAbsDiff() {
            return comparedCount == 0 ? 0 : (double) chanceAbsDiffSum / comparedCount;
        }

        double rankMeanAbsDiff() {
            return rankComparedCount == 0 ? 0 : (double) rankAbsDiffSum / rankComparedCount;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("comparedCount", comparedCount);
            map.put("chanceMeanAbsDiff", Math.round(chanceMeanAbsDiff() * 100D) / 100D);
            map.put("chanceMaxAbsDiff", chanceMaxAbsDiff);
            map.put("riskBandFlipCount", riskBandFlipCount);
            map.put("rankComparedCount", rankComparedCount);
            map.put("rankMeanAbsDiff", Math.round(rankMeanAbsDiff() * 100D) / 100D);
            return map;
        }

        String summaryLine() {
            return String.format(
                    "compared=%d chanceMeanAbsDiff=%.2f chanceMaxAbsDiff=%d riskBandFlip=%d rankMeanAbsDiff=%.0f",
                    comparedCount, chanceMeanAbsDiff(), chanceMaxAbsDiff, riskBandFlipCount, rankMeanAbsDiff());
        }
    }
}
