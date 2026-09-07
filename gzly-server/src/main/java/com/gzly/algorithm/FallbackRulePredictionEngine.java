package com.gzly.algorithm;

import com.gzly.config.FallbackPredictionProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FallbackRulePredictionEngine {

    private final FallbackPredictionProperties props;

    /** 兼容既有单测和非 Spring 场景：使用默认启发式参数。 */
    public FallbackRulePredictionEngine() {
        this(new FallbackPredictionProperties());
    }

    @Autowired
    public FallbackRulePredictionEngine(FallbackPredictionProperties props) {
        this.props = props == null ? new FallbackPredictionProperties() : props;
    }

    public Prediction predict(int candidateRank, int referenceRank, int dataConfidence) {
        return predict(candidateRank, referenceRank, 0, 0, dataConfidence / 100.0, 0);
    }

    public Prediction predict(int candidateRank, int referenceRank, double rankVolatility3y,
                              double planChangeRate, double dataConfidence, double hotTrendScore) {
        return predictWithModel(props.getModel(), candidateRank, referenceRank,
                rankVolatility3y, planChangeRate, dataConfidence, hotTrendScore);
    }

    /**
     * 指定基础模型的预测入口，供回测在 sigmoid 与 log-rank 之间做 A/B 对比。
     *
     * <p>log-rank：位次是乘性量（头部 100 名与 5 万名处的波动量级完全不同），在对数空间
     * 建模才近似正态：base = Φ( ln(参考位次/考生位次) / max(σ_min, 三年位次变异系数) )。
     * σ 已吸收位次波动，因此该模式下不再叠加波动惩罚；缩招/置信度/热度惩罚照常。
     * 默认模型仍为 sigmoid，切换须先经离线回测（/admin/backtest 支持 chanceModel 覆盖）。</p>
     */
    public Prediction predictWithModel(String model, int candidateRank, int referenceRank, double rankVolatility3y,
                                       double planChangeRate, double dataConfidence, double hotTrendScore) {
        return predictInternal(model, props.getSigmaMin(), candidateRank, referenceRank,
                rankVolatility3y, planChangeRate, dataConfidence, hotTrendScore);
    }

    /**
     * 带「省 × 批次」参数覆盖的预测入口（chance_params_json，见 FallbackPredictionProperties.Overrides）：
     * 河南等同分密度大省可按批次上调 σ 下限，而不影响其他省份的全局配置。
     */
    public Prediction predictWithOverrides(com.gzly.config.FallbackPredictionProperties.Overrides overrides,
                                           int candidateRank, int referenceRank, double rankVolatility3y,
                                           double planChangeRate, double dataConfidence, double hotTrendScore) {
        return predictInternal(props.effectiveModel(overrides), props.effectiveSigmaMin(overrides),
                candidateRank, referenceRank, rankVolatility3y, planChangeRate, dataConfidence, hotTrendScore);
    }

    private Prediction predictInternal(String model, double sigmaMin, int candidateRank, int referenceRank,
                                       double rankVolatility3y, double planChangeRate,
                                       double dataConfidence, double hotTrendScore) {
        int predicted = Math.max(0, referenceRank);
        int rankDiff = predicted - Math.max(1, candidateRank);
        boolean logRank = "log-rank".equalsIgnoreCase(model == null ? "" : model.trim());

        double baseScore;
        double volatilityPenalty;
        if (logRank) {
            double sigma = Math.max(sigmaMin, Math.max(0D, rankVolatility3y));
            double z = Math.log(Math.max(1, predicted) / (double) Math.max(1, candidateRank)) / sigma;
            baseScore = predicted <= 0 ? 0D : normalCdf(z);
            volatilityPenalty = 0D;
        } else {
            double rankScale = Math.max(props.getRankScaleMin(), Math.max(1, candidateRank) * props.getRankScaleFactor());
            baseScore = sigmoid(rankDiff / rankScale);
            volatilityPenalty = Math.min(props.getVolatilityPenaltyCap(),
                    Math.max(0D, rankVolatility3y) * props.getVolatilityPenaltyFactor());
        }

        double planPenalty = planChangeRate < 0
                ? Math.min(props.getPlanPenaltyCap(), Math.abs(planChangeRate) * props.getPlanPenaltyFactor())
                : 0D;
        double normalizedConfidence = dataConfidence > 1 ? dataConfidence / 100D : dataConfidence;
        double dataPenalty = (1D - Math.max(0D, Math.min(1D, normalizedConfidence))) * props.getDataPenaltyFactor();
        double hotPenalty = Math.min(props.getHotPenaltyCap(), Math.max(0D, hotTrendScore) * props.getHotPenaltyFactor());
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

    /** 标准正态 CDF，Abramowitz-Stegun 误差函数近似（与 AlgorithmService.normalCDF 同口径）。 */
    private static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
                * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
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
