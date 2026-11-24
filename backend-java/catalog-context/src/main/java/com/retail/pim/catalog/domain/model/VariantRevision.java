package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * VariantRevision Aggregate Root: Represents a shadow working copy for remediating variant
 * compliance and metadata defects without disrupting the live published storefront variant.
 */
public class VariantRevision extends AggregateRoot<UUID> {

    private final UUID variantId;
    private final long baseVersion;
    private RevisionState revisionState;
    private final Map<String, AttributeValue> remediatedAttributes = new HashMap<>();
    private final Instant createdAt;
    private Instant updatedAt;

    public VariantRevision(UUID id, UUID variantId, long baseVersion, RevisionState revisionState,
                           Map<String, AttributeValue> remediatedAttributes, Instant createdAt, Instant updatedAt) {
        super(id != null ? id : UUID.randomUUID());
        if (variantId == null) {
            throw new DomainException("Variant ID is required for a working revision");
        }
        this.variantId = variantId;
        this.baseVersion = baseVersion;
        this.revisionState = revisionState != null ? revisionState : RevisionState.WORKING_DRAFT;
        if (remediatedAttributes != null) {
            this.remediatedAttributes.putAll(remediatedAttributes);
        }
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static VariantRevision createWorkingCopy(UUID variantId, long baseVersion) {
        return new VariantRevision(
                UUID.randomUUID(),
                variantId,
                baseVersion,
                RevisionState.WORKING_DRAFT,
                Collections.emptyMap(),
                Instant.now(),
                Instant.now()
        );
    }

    public void updateRemediatedAttributes(Map<String, AttributeValue> newAttributes) {
        if (this.revisionState != RevisionState.WORKING_DRAFT) {
            throw new DomainException(String.format("Cannot edit revision in state [%s]. Must be in WORKING_DRAFT", this.revisionState));
        }
        if (newAttributes != null) {
            this.remediatedAttributes.putAll(newAttributes);
            this.updatedAt = Instant.now();
        }
    }

    public void submitForReview() {
        if (this.revisionState != RevisionState.WORKING_DRAFT) {
            throw new DomainException(String.format("Cannot submit revision in state [%s]. Must be in WORKING_DRAFT", this.revisionState));
        }
        this.revisionState = RevisionState.IN_REVIEW;
        this.updatedAt = Instant.now();
    }

    public void approve() {
        if (this.revisionState != RevisionState.IN_REVIEW) {
            throw new DomainException(String.format("Cannot approve revision in state [%s]. Must be IN_REVIEW", this.revisionState));
        }
        this.revisionState = RevisionState.APPROVED;
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        if (this.revisionState != RevisionState.IN_REVIEW) {
            throw new DomainException(String.format("Cannot reject revision in state [%s]. Must be IN_REVIEW", this.revisionState));
        }
        this.revisionState = RevisionState.REJECTED;
        this.updatedAt = Instant.now();
    }

    public void promote() {
        if (this.revisionState != RevisionState.APPROVED) {
            throw new DomainException(String.format("Cannot promote revision in state [%s]. Must be APPROVED", this.revisionState));
        }
        this.revisionState = RevisionState.PROMOTED;
        this.updatedAt = Instant.now();
    }

    public UUID getVariantId() {
        return variantId;
    }

    public long getBaseVersion() {
        return baseVersion;
    }

    public RevisionState getRevisionState() {
        return revisionState;
    }

    public Map<String, AttributeValue> getRemediatedAttributes() {
        return Collections.unmodifiableMap(remediatedAttributes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
