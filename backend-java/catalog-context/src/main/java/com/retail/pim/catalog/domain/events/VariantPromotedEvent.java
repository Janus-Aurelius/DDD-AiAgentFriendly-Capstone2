package com.retail.pim.catalog.domain.events;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Emitted when a working revision is successfully approved and promoted to the live aggregate.
 */
public record VariantPromotedEvent(
        UUID eventId,
        UUID variantId,
        String sku,
        long version,
        Map<String, AttributeValue> remediatedAttributes,
        Instant occurredOn
) implements DomainEvent {

    public VariantPromotedEvent(UUID variantId, String sku, long version, Map<String, AttributeValue> remediatedAttributes) {
        this(
                UUID.randomUUID(),
                variantId,
                sku,
                version,
                remediatedAttributes != null ? Collections.unmodifiableMap(remediatedAttributes) : Collections.emptyMap(),
                Instant.now()
        );
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
        return "VariantPromotedEvent";
    }
}
