package com.retail.pim.localization.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Event emitted when a RegionalCatalogItem transitions to READY_FOR_REGION.
 * Consumed by Context 6 (Channel Syndication) to project localized feeds.
 */
public record RegionalItemPublished(
        UUID eventId,
        Instant occurredOn,
        UUID regionalItemId,
        UUID globalProductId,
        String productCode,
        String regionCode,
        String localizedTitle,
        Map<String, String> attributeOverrides
) implements DomainEvent {

    public RegionalItemPublished(UUID regionalItemId, UUID globalProductId, String productCode,
                                 String regionCode, String localizedTitle, Map<String, String> attributeOverrides) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                regionalItemId,
                globalProductId,
                productCode,
                regionCode,
                localizedTitle,
                attributeOverrides != null ? Collections.unmodifiableMap(attributeOverrides) : Collections.emptyMap()
        );
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getEventType() { return "RegionalItemPublished"; }
}
