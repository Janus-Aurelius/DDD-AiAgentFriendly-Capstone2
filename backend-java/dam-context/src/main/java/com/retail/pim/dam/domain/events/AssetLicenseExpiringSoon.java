package com.retail.pim.dam.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Domain Event emitted 24 hours prior to DRMLicense expiration (UTC).
 * Provides merchandising and creative teams lead time to renew rights or replace imagery
 * before products are automatically demoted from PUBLISHED status.
 */
public record AssetLicenseExpiringSoon(
        UUID eventId,
        Instant occurredOn,
        UUID assetId,
        String licenseHolder,
        Instant expirationDate,
        Set<UUID> affectedProductIds
) implements DomainEvent {

    public AssetLicenseExpiringSoon(UUID assetId, String licenseHolder, Instant expirationDate, Set<UUID> affectedProductIds) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                assetId,
                licenseHolder,
                expirationDate,
                affectedProductIds != null ? Collections.unmodifiableSet(affectedProductIds) : Collections.emptySet()
        );
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getEventType() { return "AssetLicenseExpiringSoon"; }
}
