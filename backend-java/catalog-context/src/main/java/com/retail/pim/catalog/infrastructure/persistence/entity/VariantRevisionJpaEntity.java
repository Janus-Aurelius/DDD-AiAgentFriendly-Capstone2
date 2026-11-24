package com.retail.pim.catalog.infrastructure.persistence.entity;

import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.infrastructure.config.JsonbConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping table `catalog.variant_revision`.
 */
@Entity
@Table(name = "variant_revision", schema = "catalog")
public class VariantRevisionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "base_version", nullable = false)
    private long baseVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_state", nullable = false, length = 50)
    private RevisionState revisionState;

    @Convert(converter = JsonbConverters.AttributeMapConverter.class)
    @Column(name = "remediated_attributes", columnDefinition = "jsonb", nullable = false)
    private Map<String, AttributeValue> remediatedAttributes = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VariantRevisionJpaEntity() {}

    public VariantRevisionJpaEntity(UUID id, UUID variantId, long baseVersion, RevisionState revisionState,
                                    Map<String, AttributeValue> remediatedAttributes,
                                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.variantId = variantId;
        this.baseVersion = baseVersion;
        this.revisionState = revisionState;
        this.remediatedAttributes = remediatedAttributes != null ? remediatedAttributes : new HashMap<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public RevisionState getRevisionState() {
        return revisionState;
    }

    public void setRevisionState(RevisionState revisionState) {
        this.revisionState = revisionState;
    }

    public Map<String, AttributeValue> getRemediatedAttributes() {
        return remediatedAttributes;
    }

    public void setRemediatedAttributes(Map<String, AttributeValue> remediatedAttributes) {
        this.remediatedAttributes = remediatedAttributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
