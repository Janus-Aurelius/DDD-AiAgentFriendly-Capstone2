package com.retail.pim.compliance.domain.events;

import com.retail.pim.compliance.domain.model.ComplianceViolation;
import com.retail.pim.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event emitted when a product's compliance report has been evaluated.
 * Syndication services and Merchandising UI subscribe to this event to verify export readiness.
 */
public record ComplianceEvaluatedEvent(
        UUID eventId,
        Instant occurredOn,
        UUID reportId,
        UUID productId,
        Long productVersion,
        String targetLocale,
        String targetChannel,
        double completenessScore,
        String status,
        boolean isPublishable,
        int blockingViolationsCount,
        int warningViolationsCount,
        List<ComplianceViolation> violations
) implements DomainEvent {

    public ComplianceEvaluatedEvent(UUID reportId, UUID productId, Long productVersion, String targetLocale,
                                    String targetChannel, double completenessScore, String status,
                                    boolean isPublishable, int blockingViolationsCount, int warningViolationsCount,
                                    List<ComplianceViolation> violations) {
        this(
                UUID.randomUUID(),
                Instant.now(),
                reportId,
                productId,
                productVersion != null ? productVersion : 0L,
                targetLocale,
                targetChannel,
                completenessScore,
                status,
                isPublishable,
                blockingViolationsCount,
                warningViolationsCount,
                violations != null ? Collections.unmodifiableList(violations) : Collections.emptyList()
        );
    }

    public ComplianceEvaluatedEvent(UUID reportId, UUID productId, String targetLocale,
                                    String targetChannel, double completenessScore, String status,
                                    List<ComplianceViolation> violations) {
        this(reportId, productId, 0L, targetLocale, targetChannel, completenessScore, status,
                violations == null || violations.stream().noneMatch(ComplianceViolation::isBlocking),
                violations != null ? (int) violations.stream().filter(ComplianceViolation::isBlocking).count() : 0,
                violations != null ? (int) violations.stream().filter(ComplianceViolation::isWarning).count() : 0,
                violations);
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
        return "ComplianceEvaluated";
    }

    public int violationsCount() {
        return violations.size();
    }
}
