package com.gzly.algorithm;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class FallbackRulePredictionEngine {
    public Prediction predict(int candidateRank, int referenceRank, int dataConfidence) {
        return predict(candidateRank, referenceRank, 0, 0, dataConfidence / 100.0, 0);
    }

    public Prediction predict(int candidateRank, int referenceRank, double rankVolatility3y,
                              double planChangeRate, double dataConfidence, double hotTrendScore) {
        int predicted = Math.max(0, referenceRank);
        int rankDiff = predicted - Math.max(1, candidateRank);
        double rankScale = Math.max(1000D, Math.max(1, candidateRank) * 0.08D);
        double baseScore = sigmoid(rankDiff / rankScale);
        double volatilityPenalty = Math.min(0.20D, Math.max(0D, rankVolatility3y) * 0.5D);
        double planPenalty = planChangeRate < 0 ? Math.min(0.15D, Math.abs(planChangeRate) * 0.5D) : 0D;
        double normalizedConfidence = dataConfidence > 1 ? dataConfidence / 100D : dataConfidence;
        double dataPenalty = (1D - Math.max(0D, Math.min(1D, normalizedConfidence))) * 0.15D;
        double hotPenalty = Math.min(0.10D, Math.max(0D, hotTrendScore) * 0.1D);
        double internal = clamp(baseScore - volatilityPenalty - planPenalty - dataPenalty - hotPenalty, 0.01D, 0.99D);
        int chanceScore = (int) Math.round(internal * 100D);

        Prediction prediction = new Prediction();
        prediction.setPredictedMinRank(predicted);
        prediction.setRankDiff(rankDiff);
        prediction.setInternalChanceScore(internal);
        prediction.setChanceScore(chanceScore);
        prediction.setDataConfidence((int) Math.round(Math.max(0D, Math.min(1D, normalizedConfidence)) * 100D));
        prediction.setChanceLevel(chanceScore >= 90 ? "兜底参考" : chanceScore >= 75 ? "稳妥参考" : chanceScore >= 50 ? "适中" : "冲刺参考");
        prediction.setRiskLevel(chanceScore >= 90 ? "很低" : chanceScore >= 75 ? "较低" : chanceScore >= 50 ? "中等" : "较高");
        prediction.setConfidenceLevel(prediction.getDataConfidence() >= 85 ? "高"
                : prediction.getDataConfidence() >= 70 ? "中"
                : prediction.getDataConfidence() >= 55 ? "低" : "数据不足");
        return prediction;
    }

    private double sigmoid(double x) {
        return 1D / (1D + Math.exp(-x));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Data
    public static class Prediction {
        private int predictedMinRank;
        private int rankDiff;
        private double internalChanceScore;
        private int chanceScore;
        private String chanceLevel;
        private String riskLevel;
        private int dataConfidence;
        private String confidenceLevel;
    }
}
