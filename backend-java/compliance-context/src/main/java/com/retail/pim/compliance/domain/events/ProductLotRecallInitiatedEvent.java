package com.retail.pim.compliance.domain.events;

import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * High-priority Domain Event emitted when an emergency Class 1 safety recall is declared.
 * Dispatched to the Kafka QoS Express Lane (compliance.report.events.qos) with zero database wait.
 */
public record ProductLotRecallInitiatedEvent(
        UUID eventId,
        String sku,
        String lotNumber,
        String severity,
        String reason,
        Instant occurredOn
) implements DomainEvent {

    public ProductLotRecallInitiatedEvent(String sku, String lotNumber, String severity, String reason) {
        this(
                UUID.randomUUID(),
                sku,
                lotNumber,
                severity != null ? severity : "CLASS_1_CRITICAL_SAFETY",
                reason != null ? reason : "Emergency safety recall declared",
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
        return "ProductLotRecallInitiatedEvent";
    }
}
