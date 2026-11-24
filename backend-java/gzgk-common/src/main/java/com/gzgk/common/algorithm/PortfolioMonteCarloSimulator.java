package com.gzgk.common.algorithm;

import java.util.List;
import java.util.Random;

/**
 * Portfolio-level Monte Carlo simulator computing list-wide hit reference rate (2000 Bernoulli trials)
 * with conservative correlation discount (0.92) for same-batch / same-school dependence.
 */
public final class PortfolioMonteCarloSimulator {

    public static final int DEFAULT_ITERATIONS = 2000;
    public static final double DEFAULT_DEPENDENCY_DISCOUNT = 0.92;
    public static final long DEFAULT_SEED = 20260506L;

    private PortfolioMonteCarloSimulator() {
    }

    public static double listHitRate(List<Double> probabilities) {
        return listHitRate(probabilities, DEFAULT_ITERATIONS, DEFAULT_DEPENDENCY_DISCOUNT, DEFAULT_SEED);
    }

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
                if (raw == null) {
                    continue;
                }
                double p = Math.max(0, Math.min(1, raw)) * clampedDiscount;
                if (random.nextDouble() < p) {
                    hitAny = true;
                    break;
                }
            }
            if (hitAny) {
                hits++;
            }
        }
        return (double) hits / iterations;
    }
}
