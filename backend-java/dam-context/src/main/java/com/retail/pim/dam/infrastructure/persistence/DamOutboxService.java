package com.retail.pim.dam.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional Outbox Service for DAM Context:
 * Persists domain events into `dam.outbox_event` inside the database transaction boundary,
 * eliminating dual-write race conditions and phantom events.
 */
@Service
public class DamOutboxService {

    private static final Logger log = LoggerFactory.getLogger(DamOutboxService.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DamOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveEvent(String aggregateType, UUID aggregateId, DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String sql = """
                INSERT INTO dam.outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status)
                VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING')
                """;
            jdbcTemplate.update(sql,
                    event.getEventId() != null ? event.getEventId() : UUID.randomUUID(),
                    aggregateType,
                    aggregateId,
                    event.getEventType(),
                    payload
            );
            log.info("DAM Outbox: Staged event [{}] of type [{}] for aggregate [{}]",
                    event.getEventId(), event.getEventType(), aggregateId);
        } catch (JsonProcessingException e) {
            log.error("DAM Outbox: Failed to serialize event [{}]", event.getEventType(), e);
            throw new RuntimeException("Serialization failure in DAM outbox", e);
        }
    }
}
