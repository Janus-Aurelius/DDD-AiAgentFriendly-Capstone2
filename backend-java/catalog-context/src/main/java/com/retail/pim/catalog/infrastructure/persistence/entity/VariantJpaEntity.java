package com.retail.pim.catalog.infrastructure.persistence.entity;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.catalog.infrastructure.config.JsonbConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Persistence Entity mapping to table `catalog.product_variant`.
 */
@Entity
@Table(name = "product_variant", schema = "catalog")
public class VariantJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_family_id", nullable = false)
    private ProductJpaEntity productFamily;

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Convert(converter = JsonbConverters.DimensionsConverter.class)
    @Column(name = "dimensions", columnDefinition = "jsonb")
    private Dimensions dimensions;

    @Convert(converter = JsonbConverters.AttributeMapConverter.class)
    @Column(name = "attributes", columnDefinition = "jsonb", nullable = false)
    private Map<String, AttributeValue> attributes = new HashMap<>();

    @Convert(converter = JsonbConverters.DigitalAssetsConverter.class)
    @Column(name = "digital_assets", columnDefinition = "jsonb", nullable = false)
    private List<DigitalAsset> digitalAssets = new ArrayList<>();

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 50)
    private com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus availabilityStatus = com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus.AVAILABLE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VariantJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductJpaEntity getProductFamily() {
        return productFamily;
    }

    public void setProductFamily(ProductJpaEntity productFamily) {
        this.productFamily = productFamily;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimensions dimensions) {
        this.dimensions = dimensions;
    }

    public Map<String, AttributeValue> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, AttributeValue> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    public List<DigitalAsset> getDigitalAssets() {
        return digitalAssets;
    }

    public void setDigitalAssets(List<DigitalAsset> digitalAssets) {
        this.digitalAssets = digitalAssets != null ? digitalAssets : new ArrayList<>();
    }

    public com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
