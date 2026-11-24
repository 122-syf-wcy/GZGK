package com.gzgk.common.algorithm;

/**
 * Piecewise-isotonic probability calibration preserving exact parity with GZLY research report.
 * Maps opportunity index (0..100) monotonically to calibrated p in [0.02, 0.97] to curb overconfidence.
 */
public final class ProbabilityCalibration {

    private static final double[] SCORE_KNOTS = {0, 10, 25, 40, 55, 70, 85, 95, 100};
    private static final double[] PROB_KNOTS = {0.02, 0.04, 0.15, 0.35, 0.55, 0.72, 0.88, 0.94, 0.97};

    private ProbabilityCalibration() {
    }

    public static double fromChanceScore(int chanceScore) {
        double x = Math.max(0, Math.min(100, chanceScore));
        for (int i = 0; i < SCORE_KNOTS.length - 1; i++) {
            double lo = SCORE_KNOTS[i];
            double hi = SCORE_KNOTS[i + 1];
            if (x >= lo && x <= hi) {
                double pLo = PROB_KNOTS[i];
                double pHi = PROB_KNOTS[i + 1];
                if (hi == lo) {
                    return pLo;
                }
                double t = (x - lo) / (hi - lo);
                return pLo + t * (pHi - pLo);
            }
        }
        return PROB_KNOTS[PROB_KNOTS.length - 1];
    }

    public static double fromRawProbability(double rawProb) {
        int approxScore = (int) Math.round(Math.max(0, Math.min(1, rawProb)) * 100);
        return fromChanceScore(approxScore);
    }
}
