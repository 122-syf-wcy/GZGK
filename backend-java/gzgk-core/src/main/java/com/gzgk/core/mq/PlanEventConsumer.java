package com.gzgk.core.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzgk.core.mq.PlanEventModels.PlanPersistEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Idempotent Kafka consumer sinking plan snapshots into monthly-partitioned PostgreSQL `biz_plan_history`.
 * Uses `ON CONFLICT (event_id, created_at) DO NOTHING` for at-least-once delivery safety,
 * routing poison messages to DLQ after failure.
 */
@Component
@ConditionalOnProperty(name = "gzgk.core.kafka-enabled", havingValue = "true")
public class PlanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlanEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PlanEventConsumer(ObjectMapper objectMapper,
                             ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                             ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
    }

    @KafkaListener(topics = PlanEventModels.TOPIC_PLAN_PERSIST, groupId = "gzgk-core-persister")
    public void onPlanPersistMessage(String payload) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            PlanPersistEvent ev = objectMapper.readValue(payload, PlanPersistEvent.class);
            String resubjectsJson = objectMapper.writeValueAsString(ev.resubjects());
            jdbcTemplate.update(
                    """
                    INSERT INTO biz_plan_history (
                        plan_id, created_at, updated_at, version, event_id, user_key,
                        province_code, volunteer_unit_type, target_batch, total_score, province_rank,
                        first_subject, resubjects, strategy_mode, fingerprint, item_count, list_hit_rate,
                        plan_json, manual_review_json, metrics_json, request_snapshot_json
                    ) VALUES (
                        ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?,
                        ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb
                    )
                    ON CONFLICT (event_id, created_at) DO NOTHING
                    """,
                    ev.planId(), Timestamp.from(ev.occurredAt()), ev.version(), ev.eventId(), ev.userKey(),
                    ev.provinceCode(), ev.volunteerUnitType(), ev.targetBatch(), ev.totalScore(), ev.provinceRank(),
                    ev.firstSubject(), resubjectsJson, ev.strategyMode(), ev.fingerprint(), ev.itemCount(),
                    ev.listHitRate(), ev.planJson(), ev.manualReviewJson(), ev.metricsJson(), ev.requestSnapshotJson()
            );
        } catch (Exception ex) {
            log.error("[mq] Persist failed, routing to DLQ: {}", ex.getMessage());
            if (kafkaTemplate != null) {
                kafkaTemplate.send(PlanEventModels.TOPIC_PLAN_PERSIST_DLQ, payload);
            }
        }
    }
}
