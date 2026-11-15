package com.retail.pim.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for Domain Events across all bounded contexts.
 */
public interface DomainEvent {
    UUID getEventId();
    Instant getOccurredOn();
    String getEventType();
}
