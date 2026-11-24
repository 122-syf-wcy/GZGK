package com.gzgk.core.recommend;

import java.util.List;
import java.util.Map;

public final class RecommendModels {
    private RecommendModels() {
    }

    public record GeneratePlanRequest(
            String provinceCode,
            Integer year,
            Integer totalScore,
            Integer provinceRank,
            String firstSubject,
            List<String> resubjects,
            String strategyMode,        // balanced | school_first | major_first
            List<String> preferredMajors,
            List<String> dislikedMajors,
            List<String> preferredRegions,
            Boolean acceptPrivate,
            Boolean acceptSinoForeign,
            Integer maxTuition,
            Boolean agreedDisclaimer,
            String disclaimerVersion,
            String userKey
    ) {
        public String safeProvinceCode() {
            return (provinceCode == null || provinceCode.isBlank()) ? "GZ" : provinceCode.trim().toUpperCase();
        }

        public String safeStrategyMode() {
            return (strategyMode == null || strategyMode.isBlank()) ? "balanced" : strategyMode.trim().toLowerCase();
        }

        public String safeFirstSubject() {
            return (firstSubject == null || firstSubject.isBlank()) ? "物理" : firstSubject.replace("类", "").trim();
        }
    }

    public record GradientCounts(int sprint, int steady, int safe, int pad) {
        public int total() {
            return sprint + steady + safe + pad;
        }
    }

    public record PlanItem(
            int volunteerIndex,
            String itemId,
            String gradient,            // 冲 | 稳 | 保 | 垫
            String schoolId,
            String universityName,
            String majorCode,
            String majorName,
            String province,
            String city,
            List<String> schoolTags,
            String schoolNature,
            String tuition,
            int latestMinScore,
            int latestMinRank,
            int predictedMinRank,
            int planCount,
            double planChangeRate,
            int chanceScore,            // 0..100 compliant opportunity index
            String chanceLevel,
            double calibratedProb,
            String riskLevel,
            boolean needsManualReview,
            String manualReviewReason,
            String officialCharterUrl,
            List<Integer> recent3yRanks,
            boolean locked,
            boolean aiHighlighted
    ) {
        public PlanItem withIndex(int newIndex) {
            return new PlanItem(newIndex, itemId, gradient, schoolId, universityName, majorCode, majorName,
                    province, city, schoolTags, schoolNature, tuition, latestMinScore, latestMinRank,
                    predictedMinRank, planCount, planChangeRate, chanceScore, chanceLevel, calibratedProb,
                    riskLevel, needsManualReview, manualReviewReason, officialCharterUrl, recent3yRanks,
                    locked, aiHighlighted);
        }

        public PlanItem withHighlighted(boolean highlighted) {
            return new PlanItem(volunteerIndex, itemId, gradient, schoolId, universityName, majorCode, majorName,
                    province, city, schoolTags, schoolNature, tuition, latestMinScore, latestMinRank,
                    predictedMinRank, planCount, planChangeRate, chanceScore, chanceLevel, calibratedProb,
                    riskLevel, needsManualReview, manualReviewReason, officialCharterUrl, recent3yRanks,
                    locked, highlighted);
        }
    }

    public record PlanMetrics(
            long pipelineTimeMs,
            boolean cacheHit,
            boolean singleFlightCoalesced,
            String predictionEngine,    // ml-grpc | rule-log-rank-fallback
            int recalledCandidates,
            int hydrationQueries,
            int monteCarloIterations
    ) {
    }

    public record PlanSnapshot(
            String planId,
            int version,
            String provinceCode,
            String provinceName,
            String volunteerUnitType,
            String targetBatch,
            int totalScore,
            int provinceRank,
            String firstSubject,
            List<String> resubjects,
            String strategyMode,
            String fingerprint,
            String readinessStatus,
            GradientCounts gradientCounts,
            double listHitRate,
            int manualReviewCount,
            int shrinkWarningCount,
            List<String> diagnosisNotes,
            String disclaimer,
            PlanMetrics metrics,
            List<PlanItem> items,
            Map<String, Object> extraMeta
    ) {
    }
}
