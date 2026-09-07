package com.gzly.algorithm;

import com.gzly.config.FallbackPredictionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引擎参数可配置化回归：默认构造必须保持上线以来的启发式行为；
 * 注入配置后 sigmoid 缩放等参数生效（回测校准的调参入口）。
 */
class FallbackRulePredictionEngineConfigTest {

    @Test
    void defaultConstructor_keepsLegacyScore() {
        // rank=10000, ref=12000: rankDiff=2000, scale=max(1000, 10000*0.08)=1000, sigmoid(2)≈0.8808 → 88
        FallbackRulePredictionEngine.Prediction p =
                new FallbackRulePredictionEngine().predict(10_000, 12_000, 100);
        assertThat(p.getChanceScore()).isEqualTo(88);
        assertThat(p.getPredictedMinRank()).isEqualTo(12_000);
        assertThat(p.getRankDiff()).isEqualTo(2_000);
    }

    @Test
    void customRankScaleFactor_changesScore() {
        FallbackPredictionProperties props = new FallbackPredictionProperties();
        props.setRankScaleFactor(0.5); // scale=max(1000, 10000*0.5)=5000, sigmoid(0.4)≈0.5987 → 60
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine(props);

        FallbackRulePredictionEngine.Prediction p = engine.predict(10_000, 12_000, 100);
        assertThat(p.getChanceScore()).isEqualTo(60);
    }

    @Test
    void customVolatilityCap_limitsPenalty() {
        FallbackPredictionProperties props = new FallbackPredictionProperties();
        props.setVolatilityPenaltyCap(0.05);
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine(props);

        // 波动 0.4 * factor 0.5 = 0.2，但 cap=0.05 → 只扣 5 分
        FallbackRulePredictionEngine.Prediction capped = engine.predict(10_000, 12_000, 0.4, 0, 1.0, 0);
        FallbackRulePredictionEngine.Prediction base = engine.predict(10_000, 12_000, 0, 0, 1.0, 0);
        assertThat(base.getChanceScore() - capped.getChanceScore()).isEqualTo(5);
    }

    @Test
    void logRankModel_probabilityFollowsLogRatioDirection() {
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine();

        // 参考位次 == 考生位次 → ln(1)=0 → Φ(0)=0.5 附近
        FallbackRulePredictionEngine.Prediction even = engine.predictWithModel(
                "log-rank", 30_000, 30_000, 0.10, 0, 1.0, 0);
        assertThat(even.getChanceScore()).isBetween(45, 55);

        // 参考位次更靠后（更容易达线）→ 概率显著大于 0.5；更靠前 → 显著小于 0.5
        FallbackRulePredictionEngine.Prediction easier = engine.predictWithModel(
                "log-rank", 30_000, 45_000, 0.10, 0, 1.0, 0);
        FallbackRulePredictionEngine.Prediction harder = engine.predictWithModel(
                "log-rank", 30_000, 20_000, 0.10, 0, 1.0, 0);
        assertThat(easier.getChanceScore()).isGreaterThan(85);
        assertThat(harder.getChanceScore()).isLessThan(15);

        // 波动越大（σ 越大）→ 同样的位次差概率越向 0.5 收缩（不再额外线性扣分）
        FallbackRulePredictionEngine.Prediction lowVol = engine.predictWithModel(
                "log-rank", 30_000, 36_000, 0.06, 0, 1.0, 0);
        FallbackRulePredictionEngine.Prediction highVol = engine.predictWithModel(
                "log-rank", 30_000, 36_000, 0.40, 0, 1.0, 0);
        assertThat(lowVol.getChanceScore()).isGreaterThan(highVol.getChanceScore());
        assertThat(highVol.getChanceScore()).isGreaterThan(50);
    }

    @Test
    void javaDefaultStaysSigmoid_deploymentDefaultIsLogRankViaYml() {
        // 分层默认：Java 类内默认 sigmoid（本测试基线），部署默认由 application.yml 设为 log-rank
        //（2026-08-12 回测 A/B 达标后切换，见 docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 13.0）。
        FallbackRulePredictionEngine engine = new FallbackRulePredictionEngine();
        FallbackRulePredictionEngine.Prediction byDefault = engine.predict(10_000, 12_000, 0.1, -0.05, 0.9, 0.2);
        FallbackRulePredictionEngine.Prediction sigmoid = engine.predictWithModel(
                "sigmoid", 10_000, 12_000, 0.1, -0.05, 0.9, 0.2);
        assertThat(byDefault.getChanceScore()).isEqualTo(sigmoid.getChanceScore());
        assertThat(byDefault.getInternalChanceScore()).isEqualTo(sigmoid.getInternalChanceScore());

        // 配置注入 log-rank 后 predict 走对数位次模型
        FallbackPredictionProperties props = new FallbackPredictionProperties();
        props.setModel("log-rank");
        FallbackRulePredictionEngine logRank = new FallbackRulePredictionEngine(props);
        FallbackRulePredictionEngine.Prediction viaConfig = logRank.predict(10_000, 12_000, 0.1, -0.05, 0.9, 0.2);
        FallbackRulePredictionEngine.Prediction direct = logRank.predictWithModel(
                "log-rank", 10_000, 12_000, 0.1, -0.05, 0.9, 0.2);
        assertThat(viaConfig.getChanceScore()).isEqualTo(direct.getChanceScore());
    }
}
