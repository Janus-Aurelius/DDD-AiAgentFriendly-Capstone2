package com.retail.pim.dam.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AssetLinkedToProduct(
        UUID eventId,
        Instant occurredOn,
        UUID assetId,
        UUID productId,
        String sku,
        boolean isPrimaryImage
) implements DomainEvent {

    public AssetLinkedToProduct(UUID assetId, UUID productId, String sku, boolean isPrimaryImage) {
        this(UUID.randomUUID(), Instant.now(), assetId, productId, sku, isPrimaryImage);
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getEventType() { return "AssetLinkedToProduct"; }
}
