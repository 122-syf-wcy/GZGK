package com.gzgk.core.scoreline;

public record ScoreRankRecord(
        String provinceCode,
        int year,
        String subjectType,
        int score,
        int segmentCount,
        int cumulativeCount,
        double cumulativeRate,
        int rankLow,
        int rankHigh,
        String sourceName,
        String sourceUrl
) {
}
