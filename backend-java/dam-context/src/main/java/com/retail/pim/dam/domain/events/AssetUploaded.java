package com.retail.pim.dam.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AssetUploaded(
        UUID eventId,
        Instant occurredOn,
        UUID assetId,
        String originalFileName,
        String mimeType,
        long fileSizeBytes,
        String rawS3Url
) implements DomainEvent {

    public AssetUploaded(UUID assetId, String originalFileName, String mimeType, long fileSizeBytes, String rawS3Url) {
        this(UUID.randomUUID(), Instant.now(), assetId, originalFileName, mimeType, fileSizeBytes, rawS3Url);
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public Instant getOccurredOn() { return occurredOn; }
    @Override
    public String getEventType() { return "AssetUploaded"; }
}
