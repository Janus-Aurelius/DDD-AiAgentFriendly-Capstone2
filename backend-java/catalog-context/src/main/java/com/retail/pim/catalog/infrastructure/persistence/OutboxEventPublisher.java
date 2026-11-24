package com.retail.pim.catalog.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.ports.CatalogOutboxPort;
import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.events.ProductPublished;
import com.retail.pim.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Transactional Outbox Pattern Publisher:
 * Guarantees at-least-once domain event publication by writing events to PostgreSQL
 * in the same ACID transaction as the business entity mutation.
 */
@Component
public class OutboxEventPublisher implements CatalogOutboxPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists domain events into the outbox table within the caller's active database transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishEvents(Collection<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO catalog.outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', ?)
            """;

        for (DomainEvent event : events) {
            try {
                UUID eventId = event.getEventId();
                String eventType = event.getEventType();
                String aggregateType = determineAggregateType(event);
                UUID aggregateId = determineAggregateId(event);
                String jsonPayload = objectMapper.writeValueAsString(event);
                Instant createdAt = event.getOccurredOn() != null ? event.getOccurredOn() : Instant.now();

                jdbcTemplate.update(sql,
                        eventId,
                        aggregateType,
                        aggregateId,
                        eventType,
                        jsonPayload,
                        Timestamp.from(createdAt)
                );

                log.info("Outbox: Registered event [{}] of type [{}] for aggregate [{}]",
                        eventId, eventType, aggregateId);

            } catch (JsonProcessingException e) {
                log.error("Outbox: Failed to serialize domain event of type {}", event.getEventType(), e);
                throw new IllegalStateException("Domain event serialization failed", e);
            }
        }
    }

    private String determineAggregateType(DomainEvent event) {
        if (event instanceof ProductPublished || event instanceof ProductAttributesUpdatedEvent) {
            return "ProductFamily";
        }
        return "UnknownAggregate";
    }

    private UUID determineAggregateId(DomainEvent event) {
        if (event instanceof ProductPublished pe) {
            return pe.productId();
        }
        if (event instanceof ProductAttributesUpdatedEvent ae) {
            return ae.productId();
        }
        return event.getEventId();
    }
}
