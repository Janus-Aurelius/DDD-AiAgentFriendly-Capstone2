package com.retail.pim.bootstrap.workers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guardrail 3: Outbox Reconciliation Sweeper.
 * Background worker executing every 60s to query uncompleted outbox rows across schemas
 * (`completed_at IS NULL AND created_at < NOW() - INTERVAL '60 SECONDS'`) and re-dispatch them.
 * Commits each record's completion in an isolated transaction using Propagation.REQUIRES_NEW.
 */
@Component
public class OutboxReconciliationSweeper {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciliationSweeper.class);

    private final JdbcOperations jdbcTemplate;
    private final KafkaOperations<String, String> kafkaTemplate;

    public OutboxReconciliationSweeper(JdbcOperations jdbcTemplate,
                                       @Autowired(required = false) KafkaOperations<String, String> kafkaTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    public record ReconciledOutboxEvent(
            UUID id,
            String schemaName,
            String eventType,
            String payload,
            String destination,
            String partitionKey
    ) {}

    @Scheduled(fixedDelayString = "${pim.outbox.sweeper.interval-ms:60000}", initialDelay = 10000)
    public void reconcileOutboxEvents() {
        int reconciledCount = sweep();
        if (reconciledCount > 0) {
            log.info("Outbox reconciliation sweep completed: {} uncompleted events dispatched and committed", reconciledCount);
        }
    }

    public int sweep() {
        List<ReconciledOutboxEvent> staleEvents = fetchStaleOutboxEvents();
        int successCount = 0;

        for (ReconciledOutboxEvent event : staleEvents) {
            try {
                dispatch(event);
                markCompleted(event.id(), event.schemaName());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to reconcile outbox event [id={}, schema={}, type={}]: {}",
                        event.id(), event.schemaName(), event.eventType(), e.getMessage(), e);
            }
        }
        return successCount;
    }

    private List<ReconciledOutboxEvent> fetchStaleOutboxEvents() {
        List<ReconciledOutboxEvent> list = new ArrayList<>();
        String[] schemas = {"catalog", "compliance", "syndication"};

        for (String schema : schemas) {
            try {
                String sql = String.format("""
                    SELECT id, event_type, payload::text, destination, partition_key
                    FROM %s.outbox_event
                    WHERE completed_at IS NULL 
                      AND created_at < NOW() - INTERVAL '60 SECONDS'
                    ORDER BY created_at ASC
                    LIMIT 100
                    """, schema);

                List<ReconciledOutboxEvent> records = jdbcTemplate.query(sql, (rs, rowNum) -> new ReconciledOutboxEvent(
                        (UUID) rs.getObject("id"),
                        schema,
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("destination"),
                        rs.getString("partition_key")
                ));
                list.addAll(records);
            } catch (Exception e) {
                log.debug("Skipping stale outbox query for schema [{}] (table may not exist yet): {}", schema, e.getMessage());
            }
        }
        return list;
    }

    private void dispatch(ReconciledOutboxEvent event) {
        String destinationTopic = event.destination() != null && !event.destination().isBlank()
                ? event.destination()
                : "catalog.product.events";

        if (kafkaTemplate != null) {
            if (event.partitionKey() != null && !event.partitionKey().isBlank()) {
                kafkaTemplate.send(destinationTopic, event.partitionKey(), event.payload());
            } else {
                kafkaTemplate.send(destinationTopic, event.payload());
            }
        } else {
            log.info("[OutboxSweeper] Dispatched event [id={}, type={}] to topic [{}] (in-process / no Kafka)",
                    event.id(), event.eventType(), destinationTopic);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID id, String schema) {
        if ("catalog".equalsIgnoreCase(schema)) {
            String updateSql = "UPDATE catalog.outbox_event SET completed_at = NOW(), processed_at = NOW(), status = 'PROCESSED' WHERE id = ?";
            jdbcTemplate.update(updateSql, id);
        } else {
            String updateSql = String.format("UPDATE %s.outbox_event SET completed_at = NOW() WHERE id = ?", schema);
            jdbcTemplate.update(updateSql, id);
        }
    }
}
