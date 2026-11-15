package com.retail.pim.catalog.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event emitted when a ProductFamily successfully transitions to PUBLISHED state.
 */
public record ProductPublished(
        UUID eventId,
        Instant occurredOn,
        UUID productId,
        String productCode,
        String productName,
        UUID categoryId,
        List<String> publishedSkus
) implements DomainEvent {

    public ProductPublished(UUID productId, String productCode, String productName, UUID categoryId, List<String> publishedSkus) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                productId,
                productCode,
                productName,
                categoryId,
                publishedSkus != null ? Collections.unmodifiableList(publishedSkus) : Collections.emptyList()
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
        return "ProductPublished";
    }
}
