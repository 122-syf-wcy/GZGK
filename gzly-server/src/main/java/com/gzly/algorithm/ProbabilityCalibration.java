package com.gzly.algorithm;

/**
 * 录取概率校准（piecewise-isotonic）。
 *
 * <p>报告《贵州省高考志愿辅助系统推荐算法与梯度规则优化研究报告》P1 要求：
 * 「推荐系统真正需要的是"0.62 就大致意味着 62% 频率会发生"，而不是只要模型 AUC 高。」
 * 本类把系统当前输出的 chanceScore（0-100）通过 **分段线性单调** 的 isotonic 近似
 * 映射到校准后的概率 p ∈ [0.02, 0.97]，抑制过度自信：</p>
 *
 * <pre>
 *   chanceScore  →  p
 *   0             0.02
 *   10            0.04
 *   25            0.15
 *   40            0.35
 *   55            0.55
 *   70            0.72
 *   85            0.88
 *   95            0.94
 *   100           0.97
 * </pre>
 *
 * <p>这些断点来自贵州 2024/2025 两年后改革期的位次—录取频率经验区间，为保守版初值；
 * 等接入带真实录取回流的离线训练集后，可换成由 {@code sklearn.isotonic.IsotonicRegression}
 * 拟合的断点，而不需要改动调用方。</p>
 *
 * <p><strong>单调性保证</strong>：内部所有相邻断点满足 x↑ → p↑，保证 isotonic。</p>
 */
public final class ProbabilityCalibration {

    private static final double[] SCORE_KNOTS = {0, 10, 25, 40, 55, 70, 85, 95, 100};
    private static final double[] PROB_KNOTS = {0.02, 0.04, 0.15, 0.35, 0.55, 0.72, 0.88, 0.94, 0.97};

    static {
        for (int i = 1; i < PROB_KNOTS.length; i++) {
            if (PROB_KNOTS[i] < PROB_KNOTS[i - 1]) {
                throw new IllegalStateException("ProbabilityCalibration knots must be monotonic non-decreasing");
            }
        }
    }

    private ProbabilityCalibration() {
    }

    /**
     * 把机会指数（0-100）映射到校准后概率（0.02-0.97）。
     *
     * @param chanceScore 机会指数；可为任意整数，越界会被夹到 [0,100]
     * @return 校准后概率 p ∈ [0.02, 0.97]
     */
    public static double fromChanceScore(int chanceScore) {
        double x = Math.max(0, Math.min(100, chanceScore));
        for (int i = 0; i < SCORE_KNOTS.length - 1; i++) {
            double lo = SCORE_KNOTS[i];
            double hi = SCORE_KNOTS[i + 1];
            if (x >= lo && x <= hi) {
                double pLo = PROB_KNOTS[i];
                double pHi = PROB_KNOTS[i + 1];
                if (hi == lo) return pLo;
                double t = (x - lo) / (hi - lo);
                return pLo + t * (pHi - pLo);
            }
        }
        return PROB_KNOTS[PROB_KNOTS.length - 1];
    }

    /**
     * 已有 0-1 原始概率（如 admissionProb/100）时也做一次同口径 isotonic，避免过度自信。
     */
    public static double fromRawProbability(double rawProb) {
        int approxScore = (int) Math.round(Math.max(0, Math.min(1, rawProb)) * 100);
        return fromChanceScore(approxScore);
    }
}
