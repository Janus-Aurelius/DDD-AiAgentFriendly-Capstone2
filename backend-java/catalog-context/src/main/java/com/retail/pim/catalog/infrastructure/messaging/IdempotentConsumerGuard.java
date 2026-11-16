package com.retail.pim.catalog.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent Consumer Guard: Prevents duplicate message execution across consumer groups.
 * Guarantees exactly-once processing semantics at the application level over at-least-once Kafka brokers.
 */
@Component
public class IdempotentConsumerGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumerGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public IdempotentConsumerGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically marks a message as processed.
     *
     * @param eventId unique event identifier from the message envelope
     * @param consumerGroup consumer group identifier
     * @return true if this is the FIRST time the event is received; false if it has already been processed (duplicate)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsume(UUID eventId, String consumerGroup) {
        if (eventId == null || consumerGroup == null) {
            return true; // Fallback if no eventId available
        }

        String sql = """
            INSERT INTO catalog.consumed_messages (event_id, consumer_group, processed_at)
            VALUES (?, ?, ?)
            ON CONFLICT (event_id, consumer_group) DO NOTHING
            """;

        int rows = jdbcTemplate.update(sql, eventId, consumerGroup, Timestamp.from(Instant.now()));
        if (rows > 0) {
            log.debug("IdempotentGuard: Acquired lock for event [{}] in group [{}]", eventId, consumerGroup);
            return true; // Acquired! First time processing.
        } else {
            log.warn("IdempotentGuard: Detected duplicate event [{}] for group [{}]. Skipping processing.",
                    eventId, consumerGroup);
            return false; // Already processed!
        }
    }
}
