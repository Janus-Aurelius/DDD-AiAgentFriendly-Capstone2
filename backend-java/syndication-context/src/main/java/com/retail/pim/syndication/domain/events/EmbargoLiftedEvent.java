package com.retail.pim.syndication.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted at timestamp T when an embargo schedule expires,
 * invalidating edge gateway response caches without triggering full document re-indexing (CUJ 5).
 */
public record EmbargoLiftedEvent(
        UUID eventId,
        String sku,
        String regionCode,
        Instant embargoUntil,
        Instant occurredOn
) implements DomainEvent {

    public EmbargoLiftedEvent(String sku, String regionCode, Instant embargoUntil, Instant occurredOn) {
        this(UUID.randomUUID(), sku, regionCode, embargoUntil, occurredOn != null ? occurredOn : Instant.now());
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
        return "EmbargoLiftedEvent";
    }
}
