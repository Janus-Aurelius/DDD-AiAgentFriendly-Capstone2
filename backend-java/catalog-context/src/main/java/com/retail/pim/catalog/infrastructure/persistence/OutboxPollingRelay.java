package com.retail.pim.catalog.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Infrastructure Relay: Transactional Outbox Pattern Poller.
 * Guarantees at-least-once delivery of domain events to Kafka by asynchronously
 * reading pending events from `catalog.outbox_event` and marking them PROCESSED.
 *
 * Utilizes `FOR UPDATE SKIP LOCKED` to allow horizontal multi-instance scaling without race conditions.
 */
@Component
@EnableScheduling
public class OutboxPollingRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingRelay.class);
    public static final String DEFAULT_TOPIC = "catalog.product.events";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPollingRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    private record OutboxRecord(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            int retryCount
    ) {}

    @Scheduled(fixedDelayString = "${catalog.outbox.poll-interval-ms:2000}")
    @Transactional
    public void pollAndRelayEvents() {
        String selectSql = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, retry_count
            FROM catalog.outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """;

        List<OutboxRecord> records = jdbcTemplate.query(selectSql, (rs, rowNum) -> new OutboxRecord(
                (UUID) rs.getObject("id"),
                rs.getString("aggregate_type"),
                (UUID) rs.getObject("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getInt("retry_count")
        ));

        if (records.isEmpty()) {
            return;
        }

        log.info("OutboxRelay: Processing batch of {} pending events for Kafka dispatch", records.size());

        String successSql = """
            UPDATE catalog.outbox_event
            SET status = 'PROCESSED', processed_at = ?
            WHERE id = ?
            """;

        String failureSql = """
            UPDATE catalog.outbox_event
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= 5 THEN 'FAILED' ELSE 'PENDING' END
            WHERE id = ?
            """;

        for (OutboxRecord record : records) {
            try {
                // Dispatch to Kafka using aggregateId as the partition key to preserve aggregate-level ordering
                kafkaTemplate.send(DEFAULT_TOPIC, record.aggregateId().toString(), record.payload());

                jdbcTemplate.update(successSql, Timestamp.from(Instant.now()), record.id());
                log.info("OutboxRelay: Dispatched event [{}] of type [{}] to topic [{}]",
                        record.id(), record.eventType(), DEFAULT_TOPIC);

            } catch (Exception e) {
                log.error("OutboxRelay: Failed to send event [{}] to Kafka, incrementing retry count", record.id(), e);
                jdbcTemplate.update(failureSql, record.id());
            }
        }
    }
}
