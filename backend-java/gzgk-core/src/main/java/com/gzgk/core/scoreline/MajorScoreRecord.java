package com.gzgk.core.scoreline;

public record MajorScoreRecord(
        String provinceCode,
        String schoolId,
        String universityName,
        String majorCode,
        String majorName,
        int year,
        String subjectType,
        String batch,
        String resubjectRequirement,
        Integer minScore,
        Integer minRank,
        Integer planCount
) {
}
