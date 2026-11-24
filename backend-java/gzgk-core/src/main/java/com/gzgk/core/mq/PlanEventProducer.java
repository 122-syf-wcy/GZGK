package com.gzgk.core.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzgk.core.mq.PlanEventModels.PlanPersistEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Async write-path Kafka producer decoupling 96-volunteer generation P99 latency
 * from database I/O spikes during Gaokao score-release peaks.
 */
@Component
public class PlanEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PlanEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong publishedCount = new AtomicLong();

    public PlanEventProducer(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                             ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public void publishPlanPersist(PlanPersistEvent event) {
        if (kafkaTemplate == null) {
            // Zero-container local dev mode: count locally without blocking
            publishedCount.incrementAndGet();
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PlanEventModels.TOPIC_PLAN_PERSIST, event.planId(), payload)
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            log.error("[mq] Failed publishing PlanPersistEvent eventId={}: {}", event.eventId(), ex.getMessage());
                        } else {
                            publishedCount.incrementAndGet();
                        }
                    });
        } catch (Exception e) {
            log.error("[mq] Serialization failed for eventId={}: {}", event.eventId(), e.getMessage());
        }
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }
}
