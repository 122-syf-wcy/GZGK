package com.gzly.algorithm;

import java.util.List;
import java.util.Random;

/**
 * 列表级蒙特卡洛仿真：报告《贵州省高考志愿辅助系统推荐算法与梯度规则优化研究报告》
 * 要求的「整表至少一项录取概率（List Hit Rate）」真实仿真实现，用于替代早期
 * 基于安全垫经验折扣的估算式。
 *
 * <p>对候选列表中每条志愿的已校准概率 p_i，重复 N 次 Bernoulli 抽样，
 * 统计「整表至少命中一条」的频率。为体现报告中提到的列表内部并非完全独立：
 * 同分 / 同院校 / 同批次的候选存在相关性，我们加入轻度的保守折扣因子
 * {@code dependencyDiscount}（默认 0.92），以避免完全独立假设下对整表安全度的高估。</p>
 *
 * <p>该仿真 **只用于整表安全度**；单条志愿的预测概率仍以 {@link ProbabilityCalibration}
 * 校准后的值为准，不会被蒙特卡洛反向覆盖。</p>
 */
public final class PortfolioMonteCarloSimulator {

    public static final int DEFAULT_ITERATIONS = 2000;
    public static final double DEFAULT_DEPENDENCY_DISCOUNT = 0.92;
    public static final long DEFAULT_SEED = 20260506L;

    private PortfolioMonteCarloSimulator() {
    }

    /**
     * 计算整表至少录取一条的概率（0-1），使用默认迭代次数与保守折扣。
     *
     * @param probabilities 已经过 {@link ProbabilityCalibration} 校准的每条志愿录取概率
     */
    public static double listHitRate(List<Double> probabilities) {
        return listHitRate(probabilities, DEFAULT_ITERATIONS, DEFAULT_DEPENDENCY_DISCOUNT, DEFAULT_SEED);
    }

    /**
     * 带参数的仿真入口，便于测试 / 离线对比。
     *
     * @param probabilities      已校准概率列表
     * @param iterations         仿真次数
     * @param dependencyDiscount (0,1]，越小越保守；1 表示严格独立
     * @param seed               随机种子，保证可重现
     */
    public static double listHitRate(List<Double> probabilities,
                                     int iterations,
                                     double dependencyDiscount,
                                     long seed) {
        if (probabilities == null || probabilities.isEmpty() || iterations <= 0) {
            return 0d;
        }
        double clampedDiscount = Math.max(0.0001, Math.min(1.0, dependencyDiscount));
        Random random = new Random(seed);
        int hits = 0;
        for (int iter = 0; iter < iterations; iter++) {
            boolean hitAny = false;
            for (Double raw : probabilities) {
                if (raw == null) continue;
                double p = Math.max(0, Math.min(1, raw)) * clampedDiscount;
                if (random.nextDouble() < p) {
                    hitAny = true;
                    break;
                }
            }
            if (hitAny) hits++;
        }
        return (double) hits / iterations;
    }
}
