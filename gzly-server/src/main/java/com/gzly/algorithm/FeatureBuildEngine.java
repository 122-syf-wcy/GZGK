package com.gzly.algorithm;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FeatureBuildEngine {
    public Map<String, Object> buildBasicFeature(int candidateRank, int predictedMinRank, int planCount, double planChangeRate) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("candidate_rank", candidateRank);
        features.put("predicted_min_rank", predictedMinRank);
        features.put("rank_diff", predictedMinRank - candidateRank);
        features.put("plan_count", planCount);
        features.put("plan_change_rate", planChangeRate);
        return features;
    }

    public RankFeature buildRankFeatures(List<Integer> minRanks, List<Integer> planCounts) {
        return buildRankFeatures(minRanks, planCounts, null, null);
    }

    /**
     * 完整 10 要素位次特征：
     * 1) latestMinRank 2) avgMinRank3y 3) medianMinRank3y 4) rankVolatility3y 5) rankTrend3y
     * 6) planChangeRate 7) hasSupplement 8) firstRoundFull 9) dataMissingCount 10) dataConfidence
     *
     * 列表顺序约定：第 0 元素为最近一年，依次往前。null / 0 / 负数会被忽略。
     */
    public RankFeature buildRankFeatures(List<Integer> minRanks,
                                         List<Integer> planCounts,
                                         List<Boolean> firstRoundFullHistory,
                                         List<Boolean> hasSupplementHistory) {
        List<Integer> ranks = minRanks == null ? List.of() : minRanks.stream()
                .filter(v -> v != null && v > 0)
                .toList();
        RankFeature f = new RankFeature();
        f.setDataMissingCount(Math.max(0, 3 - ranks.size()));
        if (ranks.isEmpty()) {
            f.setDataConfidence(0.35);
            f.setConfidenceLevel("数据不足");
            applyHistoryFlags(f, firstRoundFullHistory, hasSupplementHistory);
            return f;
        }
        f.setLatestMinRank(ranks.get(0));
        f.setAvgMinRank3y(ranks.stream().mapToInt(Integer::intValue).average().orElse(ranks.get(0)));
        f.setMedianMinRank3y(median(ranks));
        f.setRankVolatility3y(volatility(ranks));
        f.setRankTrend3y(ranks.size() < 2 ? 0 : ranks.get(0) - ranks.get(ranks.size() - 1));
        f.setPlanChangeRate(planChangeRate(planCounts));
        double confidence = ranks.size() >= 3 ? 0.92 : ranks.size() == 2 ? 0.76 : 0.58;
        if (f.getRankVolatility3y() > 0.35) confidence -= 0.10;
        f.setDataConfidence(Math.max(0.35, Math.min(0.98, confidence)));
        f.setConfidenceLevel(f.getDataConfidence() >= 0.85 ? "高"
                : f.getDataConfidence() >= 0.70 ? "中"
                : f.getDataConfidence() >= 0.55 ? "低" : "数据不足");
        applyHistoryFlags(f, firstRoundFullHistory, hasSupplementHistory);
        return f;
    }

    private void applyHistoryFlags(RankFeature f,
                                   List<Boolean> firstRoundFullHistory,
                                   List<Boolean> hasSupplementHistory) {
        f.setFirstRoundFull(latestNonNull(firstRoundFullHistory));
        f.setHasSupplement(latestNonNull(hasSupplementHistory));
    }

    private boolean latestNonNull(List<Boolean> values) {
        if (values == null) return false;
        for (Boolean v : values) {
            if (v != null) return v;
        }
        return false;
    }

    private double median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(mid);
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private double volatility(List<Integer> ranks) {
        if (ranks.size() < 2) return 0;
        double avg = ranks.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = ranks.stream().mapToDouble(v -> Math.pow(v - avg, 2)).sum() / ranks.size();
        return avg <= 0 ? 0 : Math.sqrt(variance) / avg;
    }

    private double planChangeRate(List<Integer> planCounts) {
        List<Integer> plans = planCounts == null ? List.of() : planCounts.stream()
                .filter(v -> v != null && v > 0)
                .toList();
        if (plans.size() < 2) return 0;
        int latest = plans.get(0);
        int previous = plans.get(1);
        return previous <= 0 ? 0 : (latest - previous) * 1.0 / previous;
    }

    @Data
    public static class RankFeature {
        private Integer latestMinRank;
        private double avgMinRank3y;
        private double medianMinRank3y;
        private double rankVolatility3y;
        private double rankTrend3y;
        private double planChangeRate;
        private int dataMissingCount;
        private double dataConfidence;
        private String confidenceLevel;
        /** 上一年是否一轮投档投满（false 时风险更高，可能存在征集）。 */
        private boolean firstRoundFull;
        /** 上一年是否进入征集志愿。 */
        private boolean hasSupplement;
    }
}
