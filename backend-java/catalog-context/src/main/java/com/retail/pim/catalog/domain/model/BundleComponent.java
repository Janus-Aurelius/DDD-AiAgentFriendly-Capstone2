package com.retail.pim.catalog.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a component dependency required by a bundle variant.
 */
public class BundleComponent {

    private final UUID id;
    private final UUID bundleVariantId;
    private final String componentSku;
    private final int requiredQty;
    private final Instant createdAt;

    public BundleComponent(UUID id, UUID bundleVariantId, String componentSku, int requiredQty, Instant createdAt) {
        if (bundleVariantId == null) {
            throw new IllegalArgumentException("Bundle variant ID cannot be null");
        }
        if (componentSku == null || componentSku.isBlank()) {
            throw new IllegalArgumentException("Component SKU cannot be null or blank");
        }
        if (requiredQty <= 0) {
            throw new IllegalArgumentException("Required quantity must be greater than zero");
        }

        this.id = id != null ? id : UUID.randomUUID();
        this.bundleVariantId = bundleVariantId;
        this.componentSku = componentSku.trim();
        this.requiredQty = requiredQty;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static BundleComponent of(UUID bundleVariantId, String componentSku, int requiredQty) {
        return new BundleComponent(UUID.randomUUID(), bundleVariantId, componentSku, requiredQty, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getBundleVariantId() {
        return bundleVariantId;
    }

    public String getComponentSku() {
        return componentSku;
    }

    public int getRequiredQty() {
        return requiredQty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleComponent that)) return false;
        return Objects.equals(bundleVariantId, that.bundleVariantId) &&
               Objects.equals(componentSku, that.componentSku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundleVariantId, componentSku);
    }
}
