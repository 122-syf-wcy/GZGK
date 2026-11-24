package com.gzgk.core.mq;

import java.time.Instant;
import java.util.List;

public final class PlanEventModels {

    public static final String TOPIC_PLAN_PERSIST = "gzgk.plan.persist";
    public static final String TOPIC_PLAN_PERSIST_DLQ = "gzgk.plan.persist.dlq";
    public static final String TOPIC_AI_AUDIT = "gzgk.ai.audit";

    private PlanEventModels() {
    }

    public record PlanPersistEvent(
            String eventId,
            String planId,
            int version,
            String userKey,
            String provinceCode,
            String volunteerUnitType,
            String targetBatch,
            int totalScore,
            int provinceRank,
            String firstSubject,
            List<String> resubjects,
            String strategyMode,
            String fingerprint,
            int itemCount,
            double listHitRate,
            String planJson,
            String manualReviewJson,
            String metricsJson,
            String requestSnapshotJson,
            Instant occurredAt
    ) {
    }
}
