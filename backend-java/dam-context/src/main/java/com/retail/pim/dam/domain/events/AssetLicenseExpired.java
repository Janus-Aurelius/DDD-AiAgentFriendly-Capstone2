package com.retail.pim.dam.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Domain Event emitted when an asset's DRMLicense reaches expiration.
 * Listened to by the Core Catalog to strip asset links and enforce compliance gates.
 */
public record AssetLicenseExpired(
        UUID eventId,
        Instant occurredOn,
        UUID assetId,
        String licenseHolder,
        Instant expiredAt,
        Set<UUID> affectedProductIds
) implements DomainEvent {

    public AssetLicenseExpired(UUID assetId, String licenseHolder, Instant expiredAt, Set<UUID> affectedProductIds) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                assetId,
                licenseHolder,
                expiredAt,
                affectedProductIds != null ? Collections.unmodifiableSet(affectedProductIds) : Collections.emptySet()
        );
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getEventType() { return "AssetLicenseExpired"; }
}
