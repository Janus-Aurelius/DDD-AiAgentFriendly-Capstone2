package com.retail.pim.localization.infrastructure.persistence;

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
 * Infrastructure Relay: Transactional Outbox Pattern Poller for Localization context.
 * Reads pending records from `localization.outbox_event` using `FOR UPDATE SKIP LOCKED`
 * and reliably dispatches them to topic `localization.regional.events`.
 */
@Component
@EnableScheduling
public class LocalizationOutboxPollingRelay {

    private static final Logger log = LoggerFactory.getLogger(LocalizationOutboxPollingRelay.class);
    public static final String REGIONAL_TOPIC = "localization.regional.events";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public LocalizationOutboxPollingRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate) {
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

    @Scheduled(fixedDelayString = "${localization.outbox.poll-interval-ms:2000}")
    @Transactional
    public void pollAndRelayEvents() {
        String selectSql = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, retry_count
            FROM localization.outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 50
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

        log.info("Localization OutboxRelay: Processing batch of {} pending events", records.size());

        String successSql = """
            UPDATE localization.outbox_event
            SET status = 'PROCESSED', processed_at = ?
            WHERE id = ?
            """;

        String failureSql = """
            UPDATE localization.outbox_event
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= 5 THEN 'FAILED' ELSE 'PENDING' END
            WHERE id = ?
            """;

        for (OutboxRecord record : records) {
            try {
                kafkaTemplate.send(REGIONAL_TOPIC, record.aggregateId().toString(), record.payload());
                jdbcTemplate.update(successSql, Timestamp.from(Instant.now()), record.id());
                log.info("Localization OutboxRelay: Dispatched event [{}] to topic [{}]", record.id(), REGIONAL_TOPIC);
            } catch (Exception e) {
                log.error("Localization OutboxRelay: Failed to dispatch event [{}]", record.id(), e);
                jdbcTemplate.update(failureSql, record.id());
            }
        }
    }
}
