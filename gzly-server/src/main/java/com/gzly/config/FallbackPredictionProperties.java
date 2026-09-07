package com.gzly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FallbackRulePredictionEngine 的可配置参数。
 *
 * 默认值即上线以来的启发式取值；回测（/admin/backtest）产出校准报告后，
 * 可通过配置调参并复跑回测对比，不再需要改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gzly.algo.fallback")
public class FallbackPredictionProperties {

    /** 基础概率模型：sigmoid（上线以来的启发式，默认）/ log-rank（对数位次正态 CDF，回测达标后切换）。 */
    private String model = "sigmoid";

    /**
     * log-rank 模型的位次波动下限 σ_min（对数空间标准差下限）。
     * 单年数据波动为 0 时以此为准；河南等同分密度大省应通过配置上调。
     */
    private double sigmaMin = 0.06;

    /**
     * 院校专业组省份的退档折减先验 P(退档|已投档)。
     * 1:1 投档省（四川/广西官方口径）约 0.01，≤105% 差额投档省 0.02-0.05；
     * 未核验省份取保守中值，后续按省放入 policy_rule_config.chance_params_json 覆盖。
     */
    private double groupWithdrawPrior = 0.03;

    /** sigmoid 位次缩放系数：scale = max(rankScaleMin, candidateRank * rankScaleFactor)。 */
    private double rankScaleFactor = 0.08;

    /** sigmoid 位次缩放下限。 */
    private double rankScaleMin = 1000;

    /** 位次波动惩罚上限。 */
    private double volatilityPenaltyCap = 0.20;

    /** 位次波动惩罚系数（乘以 3 年位次变异系数）。 */
    private double volatilityPenaltyFactor = 0.5;

    /** 缩招惩罚上限。 */
    private double planPenaltyCap = 0.15;

    /** 缩招惩罚系数（乘以计划缩减比例绝对值）。 */
    private double planPenaltyFactor = 0.5;

    /** 数据置信度惩罚系数（乘以 1 - 置信度）。 */
    private double dataPenaltyFactor = 0.15;

    /** 热度惩罚上限。 */
    private double hotPenaltyCap = 0.10;

    /** 热度惩罚系数。 */
    private double hotPenaltyFactor = 0.1;

    /**
     * 按「省 × 批次」的参数覆盖，来自 policy_rule_config.chance_params_json。
     * 只覆盖显式给出的字段，其余沿用全局配置；解析失败按无覆盖处理。
     * JSON 形如 {"model":"log-rank","sigmaMin":0.12,"groupWithdrawPrior":0.02}。
     */
    @Data
    public static class Overrides {
        private String model;
        private Double sigmaMin;
        private Double groupWithdrawPrior;

        public static Overrides parse(String json, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            if (json == null || json.isBlank() || objectMapper == null) {
                return null;
            }
            try {
                return objectMapper.readValue(json, Overrides.class);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public String effectiveModel(Overrides overrides) {
        return overrides != null && overrides.getModel() != null && !overrides.getModel().isBlank()
                ? overrides.getModel() : model;
    }

    public double effectiveSigmaMin(Overrides overrides) {
        return overrides != null && overrides.getSigmaMin() != null && overrides.getSigmaMin() > 0
                ? overrides.getSigmaMin() : sigmaMin;
    }

    public double effectiveGroupWithdrawPrior(Overrides overrides) {
        return overrides != null && overrides.getGroupWithdrawPrior() != null && overrides.getGroupWithdrawPrior() >= 0
                ? overrides.getGroupWithdrawPrior() : groupWithdrawPrior;
    }
}
