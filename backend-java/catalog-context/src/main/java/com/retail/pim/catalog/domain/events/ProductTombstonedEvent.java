package com.retail.pim.catalog.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted when a product variant is permanently decommissioned or tombstoned (CUJ 8).
 * Downstream read-model projectors consume this event to immediately purge denormalized cache documents
 * while PIM retains the soft-deleted relational record for historical warranty queries.
 */
public record ProductTombstonedEvent(
        UUID eventId,
        String sku,
        UUID familyId,
        String reason,
        Instant occurredOn
) implements DomainEvent {

    public ProductTombstonedEvent(String sku, UUID familyId, String reason, Instant occurredOn) {
        this(UUID.randomUUID(), sku, familyId, reason, occurredOn != null ? occurredOn : Instant.now());
    }

    public ProductTombstonedEvent(String sku, UUID familyId, String reason) {
        this(UUID.randomUUID(), sku, familyId, reason, Instant.now());
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }

    @Override
    public String getEventType() {
        return "ProductTombstonedEvent";
    }
}
