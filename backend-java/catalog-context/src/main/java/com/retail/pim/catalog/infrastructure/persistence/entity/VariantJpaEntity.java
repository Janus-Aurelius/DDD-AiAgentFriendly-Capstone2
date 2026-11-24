package com.retail.pim.catalog.infrastructure.persistence.entity;

import com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.catalog.infrastructure.config.JsonbConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 50)
    private VariantAvailabilityStatus availabilityStatus = VariantAvailabilityStatus.AVAILABLE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // FSM & Two-Tier availability mappings
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 50)
    private VariantLifecycleState lifecycleState = VariantLifecycleState.SKU_DRAFT;

    @Convert(converter = JsonbConverters.StringSetConverter.class)
    @Column(name = "active_suspension_reasons", columnDefinition = "jsonb", nullable = false)
    private Set<String> activeSuspensionReasons = new HashSet<>();

    @Convert(converter = JsonbConverters.StringMapConverter.class)
    @Column(name = "channel_status_map", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> channelStatusMap = new HashMap<>();

    @Convert(converter = JsonbConverters.StringMapConverter.class)
    @Column(name = "market_compliance_map", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> marketComplianceMap = new HashMap<>();

    @Convert(converter = JsonbConverters.StringSetConverter.class)
    @Column(name = "suspended_components", columnDefinition = "jsonb", nullable = false)
    private Set<String> suspendedComponents = new HashSet<>();

    @Column(name = "embargo_until")
    private Instant embargoUntil;

    @Column(name = "street_date")
    private Instant streetDate;

    @Column(name = "is_bestseller", nullable = false)
    private boolean bestseller = false;

    @Column(name = "display_priority", nullable = false)
    private int displayPriority = 0;

    @Column(name = "published_version", nullable = false)
    private long publishedVersion = 1L;

    @Column(name = "gtin", length = 14)
    private String gtin;

    @Column(name = "fulfillment_mode", length = 50)
    private String fulfillmentMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VariantJpaEntity() {}

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
        this.attributes = attributes;
    }

    public List<DigitalAsset> getDigitalAssets() {
        return digitalAssets;
    }

    public void setDigitalAssets(List<DigitalAsset> digitalAssets) {
        this.digitalAssets = digitalAssets;
    }

    public VariantAvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(VariantAvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public VariantLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(VariantLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public Set<String> getActiveSuspensionReasons() {
        return activeSuspensionReasons;
    }

    public void setActiveSuspensionReasons(Set<String> activeSuspensionReasons) {
        this.activeSuspensionReasons = activeSuspensionReasons;
    }

    public Map<String, String> getChannelStatusMap() {
        return channelStatusMap;
    }

    public void setChannelStatusMap(Map<String, String> channelStatusMap) {
        this.channelStatusMap = channelStatusMap;
    }

    public Map<String, String> getMarketComplianceMap() {
        return marketComplianceMap;
    }

    public void setMarketComplianceMap(Map<String, String> marketComplianceMap) {
        this.marketComplianceMap = marketComplianceMap;
    }

    public Set<String> getSuspendedComponents() {
        return suspendedComponents;
    }

    public void setSuspendedComponents(Set<String> suspendedComponents) {
        this.suspendedComponents = suspendedComponents;
    }

    public Instant getEmbargoUntil() {
        return embargoUntil;
    }

    public void setEmbargoUntil(Instant embargoUntil) {
        this.embargoUntil = embargoUntil;
    }

    public Instant getStreetDate() {
        return streetDate;
    }

    public void setStreetDate(Instant streetDate) {
        this.streetDate = streetDate;
    }

    public boolean isBestseller() {
        return bestseller;
    }

    public void setBestseller(boolean bestseller) {
        this.bestseller = bestseller;
    }

    public int getDisplayPriority() {
        return displayPriority;
    }

    public void setDisplayPriority(int displayPriority) {
        this.displayPriority = displayPriority;
    }

    public long getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(long publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(String fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
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
