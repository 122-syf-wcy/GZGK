package com.gzgk.common.algorithm;

/**
 * Validated log-rank normal CDF chance model (Brier 0.0155 vs sigmoid 0.0217 on 70,205 eval pairs).
 * Used both as deterministic baseline and as instant circuit-breaker fallback when gzgk-ml is degraded.
 */
public final class LogRankChanceModel {

    public static final double DEFAULT_SIGMA_MIN = 0.06;
    private static final double PLAN_PENALTY_CAP = 0.15;
    private static final double PLAN_PENALTY_FACTOR = 0.50;
    private static final double DATA_PENALTY_FACTOR = 0.15;
    private static final double HOT_PENALTY_CAP = 0.10;
    private static final double HOT_PENALTY_FACTOR = 0.10;

    private LogRankChanceModel() {
    }

    public static Prediction predict(int candidateRank,
                                     int referenceRank,
                                     double rankVolatility3y,
                                     double planChangeRate,
                                     double dataConfidence,
                                     double hotTrendScore,
                                     double sigmaMin) {
        int predicted = Math.max(0, referenceRank);
        int safeCandidate = Math.max(1, candidateRank);
        int rankDiff = predicted - safeCandidate;
        double sigma = Math.max(sigmaMin > 0 ? sigmaMin : DEFAULT_SIGMA_MIN, Math.max(0d, rankVolatility3y));
        double baseScore;
        if (predicted <= 0) {
            baseScore = 0d;
        } else {
            double z = Math.log((double) predicted / safeCandidate) / sigma;
            baseScore = normalCdf(z);
        }

        double planPenalty = planChangeRate < 0
                ? Math.min(PLAN_PENALTY_CAP, Math.abs(planChangeRate) * PLAN_PENALTY_FACTOR)
                : 0d;
        double normConf = dataConfidence > 1 ? dataConfidence / 100d : dataConfidence;
        normConf = clamp(normConf, 0d, 1d);
        double dataPenalty = (1d - normConf) * DATA_PENALTY_FACTOR;
        double hotPenalty = Math.min(HOT_PENALTY_CAP, Math.max(0d, hotTrendScore) * HOT_PENALTY_FACTOR);

        double internal = clamp(baseScore - planPenalty - dataPenalty - hotPenalty, 0.01d, 0.99d);
        int chanceScore = (int) Math.round(internal * 100d);
        double calibratedProb = ProbabilityCalibration.fromChanceScore(chanceScore);
        String chanceLevel = chanceScore >= 90 ? "兜底参考"
                : chanceScore >= 75 ? "稳妥参考"
                : chanceScore >= 50 ? "适中" : "冲刺参考";
        String riskLevel = chanceScore >= 90 ? "很低"
                : chanceScore >= 75 ? "较低"
                : chanceScore >= 50 ? "中等" : "较高";
        String gradient = chanceScore >= 88 ? "垫"
                : chanceScore >= 74 ? "保"
                : chanceScore >= 52 ? "稳" : "冲";

        return new Prediction(
                predicted,
                rankDiff,
                internal,
                chanceScore,
                calibratedProb,
                chanceLevel,
                riskLevel,
                gradient,
                (int) Math.round(normConf * 100d)
        );
    }

    public static double normalCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
                * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public record Prediction(
            int predictedMinRank,
            int rankDiff,
            double internalChanceScore,
            int chanceScore,
            double calibratedProb,
            String chanceLevel,
            String riskLevel,
            String gradient,
            int dataConfidence
    ) {
    }
}
