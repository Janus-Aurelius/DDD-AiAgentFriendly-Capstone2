package com.retail.pim.catalog.domain.events;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Event emitted whenever a ProductFamily's attributes are created or modified.
 * Triggers asynchronous evaluation by the Compliance Engine across configured scopes.
 * Carries productVersion to mitigate eventual consistency race conditions between UI and background processing.
 */
public record ProductAttributesUpdatedEvent(
        UUID eventId,
        Instant occurredOn,
        UUID productId,
        Long productVersion,
        UUID categoryId,
        Map<String, AttributeValue> attributes
) implements DomainEvent {

    public ProductAttributesUpdatedEvent(UUID productId, Long productVersion, UUID categoryId, Map<String, AttributeValue> attributes) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                productId,
                productVersion != null ? productVersion : 0L,
                categoryId,
                attributes != null ? Collections.unmodifiableMap(new HashMap<>(attributes)) : Collections.emptyMap()
        );
    }

    public ProductAttributesUpdatedEvent(UUID productId, UUID categoryId, Map<String, AttributeValue> attributes) {
        this(productId, 0L, categoryId, attributes);
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
        return "ProductAttributesUpdated";
    }
}
