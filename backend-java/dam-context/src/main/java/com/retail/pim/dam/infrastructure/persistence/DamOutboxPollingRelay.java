package com.retail.pim.dam.infrastructure.persistence;

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
 * Infrastructure Relay: Transactional Outbox Pattern Poller for DAM context.
 * Reads pending records from `dam.outbox_event` using `FOR UPDATE SKIP LOCKED`
 * and reliably dispatches them to topic `dam.asset.events`.
 */
@Component
@EnableScheduling
public class DamOutboxPollingRelay {

    private static final Logger log = LoggerFactory.getLogger(DamOutboxPollingRelay.class);
    public static final String DAM_TOPIC = "dam.asset.events";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DamOutboxPollingRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate) {
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

    @Scheduled(fixedDelayString = "${dam.outbox.poll-interval-ms:2000}")
    @Transactional
    public void pollAndRelayEvents() {
        String selectSql = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, retry_count
            FROM dam.outbox_event
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

        log.info("DAM OutboxRelay: Processing batch of {} pending events", records.size());

        String successSql = """
            UPDATE dam.outbox_event
            SET status = 'PROCESSED', processed_at = ?
            WHERE id = ?
            """;

        String failureSql = """
            UPDATE dam.outbox_event
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= 5 THEN 'FAILED' ELSE 'PENDING' END
            WHERE id = ?
            """;

        for (OutboxRecord record : records) {
            try {
                kafkaTemplate.send(DAM_TOPIC, record.aggregateId().toString(), record.payload());
                jdbcTemplate.update(successSql, Timestamp.from(Instant.now()), record.id());
                log.info("DAM OutboxRelay: Dispatched event [{}] to topic [{}]", record.id(), DAM_TOPIC);
            } catch (Exception e) {
                log.error("DAM OutboxRelay: Failed to dispatch event [{}]", record.id(), e);
                jdbcTemplate.update(failureSql, record.id());
            }
        }
    }
}
