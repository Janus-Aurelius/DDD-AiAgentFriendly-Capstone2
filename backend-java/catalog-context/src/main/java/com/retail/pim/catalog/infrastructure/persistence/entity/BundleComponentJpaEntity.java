package com.retail.pim.catalog.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bundle_component", schema = "catalog")
public class BundleComponentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "bundle_variant_id", nullable = false)
    private UUID bundleVariantId;

    @Column(name = "component_sku", nullable = false, length = 100)
    private String componentSku;

    @Column(name = "required_qty", nullable = false)
    private int requiredQty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BundleComponentJpaEntity() {}

    public BundleComponentJpaEntity(UUID id, UUID bundleVariantId, String componentSku, int requiredQty, Instant createdAt) {
        this.id = id;
        this.bundleVariantId = bundleVariantId;
        this.componentSku = componentSku;
        this.requiredQty = requiredQty;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBundleVariantId() {
        return bundleVariantId;
    }

    public void setBundleVariantId(UUID bundleVariantId) {
        this.bundleVariantId = bundleVariantId;
    }

    public String getComponentSku() {
        return componentSku;
    }

    public void setComponentSku(String componentSku) {
        this.componentSku = componentSku;
    }

    public int getRequiredQty() {
        return requiredQty;
    }

    public void setRequiredQty(int requiredQty) {
        this.requiredQty = requiredQty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
