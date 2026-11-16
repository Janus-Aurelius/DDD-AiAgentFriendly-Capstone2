package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.domain.Entity;
import com.retail.pim.shared.exceptions.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Variant Entity: Represents a concrete physical SKU (e.g. 65-inch OLED TV).
 * Pure Java - zero framework dependencies.
 */
public class Variant extends Entity<UUID> {

    private final String sku;
    private String name;
    private BigDecimal price;
    private String currency;
    private Dimensions dimensions;
    private final Map<String, AttributeValue> attributes = new HashMap<>();
    private final List<DigitalAsset> digitalAssets = new ArrayList<>();
    private VariantAvailabilityStatus availabilityStatus;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Variant(UUID id, String sku, String name, BigDecimal price, String currency,
                   Dimensions dimensions, Map<String, AttributeValue> attributes,
                   List<DigitalAsset> digitalAssets, VariantAvailabilityStatus availabilityStatus, boolean active) {
        super(id != null ? id : UUID.randomUUID());

        if (sku == null || sku.trim().isEmpty()) {
            throw new DomainException("Variant SKU cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Variant name cannot be empty");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Variant price must be non-negative");
        }

        this.sku = sku.trim().toUpperCase();
        this.name = name.trim();
        this.price = price;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency.trim().toUpperCase() : "USD";
        this.dimensions = dimensions;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        if (digitalAssets != null) {
            this.digitalAssets.addAll(digitalAssets);
        }
        this.availabilityStatus = availabilityStatus != null ? availabilityStatus :
                (hasWeightAndDimensions() && hasPrimaryImage() ? VariantAvailabilityStatus.AVAILABLE : VariantAvailabilityStatus.SKU_INCOMPLETE);
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Variant(UUID id, String sku, String name, BigDecimal price, String currency,
                   Dimensions dimensions, Map<String, AttributeValue> attributes,
                   List<DigitalAsset> digitalAssets, boolean active) {
        this(id, sku, name, price, currency, dimensions, attributes, digitalAssets, null, active);
    }

    public boolean hasWeightAndDimensions() {
        return dimensions != null &&
               dimensions.getWeight() != null &&
               dimensions.getWeight().compareTo(BigDecimal.ZERO) > 0 &&
               dimensions.getLength() != null &&
               dimensions.getWidth() != null &&
               dimensions.getHeight() != null;
    }

    public boolean hasPrimaryImage() {
        return digitalAssets.stream().anyMatch(DigitalAsset::isPrimaryImage);
    }

    public boolean isPublishable() {
        return active &&
               availabilityStatus == VariantAvailabilityStatus.AVAILABLE &&
               hasWeightAndDimensions() &&
               hasPrimaryImage();
    }

    public void evaluateAvailability() {
        if (this.availabilityStatus != VariantAvailabilityStatus.DISCONTINUED) {
            if (hasWeightAndDimensions() && hasPrimaryImage()) {
                this.availabilityStatus = VariantAvailabilityStatus.AVAILABLE;
            } else {
                this.availabilityStatus = VariantAvailabilityStatus.SKU_INCOMPLETE;
            }
            this.updatedAt = Instant.now();
        }
    }

    public void setDimensions(Dimensions dimensions) {
        if (dimensions == null) {
            throw new DomainException("Dimensions cannot be null");
        }
        this.dimensions = dimensions;
        evaluateAvailability();
    }

    public void addDigitalAsset(DigitalAsset asset) {
        if (asset == null) {
            throw new DomainException("Digital asset cannot be null");
        }
        if (asset.isPrimaryImage()) {
            for (int i = 0; i < digitalAssets.size(); i++) {
                DigitalAsset current = digitalAssets.get(i);
                if (current.isPrimaryImage()) {
                    digitalAssets.set(i, new DigitalAsset(current.getId(), current.getUrl(), current.getType(), false));
                }
            }
        }
        this.digitalAssets.add(asset);
        evaluateAvailability();
    }

    public boolean removeDigitalAsset(UUID assetId) {
        if (assetId == null) return false;
        boolean removed = this.digitalAssets.removeIf(a -> a.getId().equals(assetId));
        if (removed) {
            evaluateAvailability();
        }
        return removed;
    }

    public void updatePrice(BigDecimal newPrice, String currency) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Price must be non-negative");
        }
        this.price = newPrice;
        if (currency != null && !currency.trim().isEmpty()) {
            this.currency = currency.trim().toUpperCase();
        }
        this.updatedAt = Instant.now();
    }

    public void setAttribute(String key, AttributeValue value) {
        if (key == null || key.trim().isEmpty() || value == null) {
            throw new DomainException("Attribute key and value cannot be null or empty");
        }
        this.attributes.put(key.trim(), value);
        this.updatedAt = Instant.now();
    }

    public void markDiscontinued() {
        this.availabilityStatus = VariantAvailabilityStatus.DISCONTINUED;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        evaluateAvailability();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public Map<String, AttributeValue> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<DigitalAsset> getDigitalAssets() {
        return Collections.unmodifiableList(digitalAssets);
    }

    public VariantAvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
