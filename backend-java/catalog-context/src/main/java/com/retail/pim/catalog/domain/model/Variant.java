package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.RestrictionReason;
import com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Variant Entity: Represents a concrete physical SKU (e.g. 65-inch OLED TV).
 * Governed by the 8-state Finite State Machine and Two-Tier availability model.
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

    // FSM & Two-Tier availability fields
    private VariantLifecycleState lifecycleState = VariantLifecycleState.SKU_DRAFT;
    private final Map<String, String> marketComplianceMap = new HashMap<>();
    private final Map<String, String> channelStatusMap = new HashMap<>();
    private final Set<String> activeSuspensionReasons = new HashSet<>();
    private final Set<String> suspendedComponents = new HashSet<>();
    private Instant embargoUntil;
    private Instant streetDate;
    private boolean isBestseller = false;
    private int displayPriority = 0;
    private long publishedVersion = 1L;
    private String gtin;
    private String fulfillmentMode;

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
        this.lifecycleState = active ? VariantLifecycleState.SKU_ACTIVE : VariantLifecycleState.SKU_DRAFT;
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
               (lifecycleState == VariantLifecycleState.SKU_ACTIVE || availabilityStatus == VariantAvailabilityStatus.AVAILABLE) &&
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

    // ==========================================================================
    // TWO-TIER REGIONAL & CHANNEL MASKING
    // ==========================================================================

    public void applyRegionalMask(String region, RestrictionReason reason) {
        if (region == null || region.isBlank()) {
            throw new DomainException("Region code cannot be empty for regional mask");
        }
        String reasonStr = (reason != null ? reason.name() : RestrictionReason.RESTRICTED.name());
        this.marketComplianceMap.put(region.trim(), reasonStr);
        this.updatedAt = Instant.now();
    }

    public void clearRegionalMask(String region) {
        if (region != null && !region.isBlank()) {
            this.marketComplianceMap.remove(region.trim());
            this.updatedAt = Instant.now();
        }
    }

    public boolean isRegionallyCompliant(String region) {
        if (region == null || region.isBlank()) {
            return true;
        }
        String mask = this.marketComplianceMap.get(region.trim());
        if (mask == null) {
            return true;
        }
        return "COMPLIANT".equalsIgnoreCase(mask) || "ACTIVE".equalsIgnoreCase(mask);
    }

    public void applyChannelStatus(String channel, String status) {
        if (channel != null && !channel.isBlank() && status != null) {
            this.channelStatusMap.put(channel.trim(), status.trim());
            this.updatedAt = Instant.now();
        }
    }

    // ==========================================================================
    // 8-STATE FSM TRANSITION ENFORCEMENT
    // ==========================================================================

    public void transitionTo(VariantLifecycleState newState) {
        if (newState == null) {
            throw new DomainException("Target lifecycle state cannot be null");
        }
        if (this.lifecycleState == newState) {
            return;
        }

        validateStateTransition(this.lifecycleState, newState);
        this.lifecycleState = newState;
        if (newState == VariantLifecycleState.SKU_DISCONTINUED || newState == VariantLifecycleState.SKU_ARCHIVED) {
            this.active = false;
            this.availabilityStatus = VariantAvailabilityStatus.DISCONTINUED;
        } else if (newState == VariantLifecycleState.SKU_RECALLED_LOT_HOLD || newState == VariantLifecycleState.SKU_SUSPENDED) {
            this.active = false;
        } else if (newState == VariantLifecycleState.SKU_ACTIVE) {
            this.active = true;
            this.availabilityStatus = VariantAvailabilityStatus.AVAILABLE;
        }
        this.updatedAt = Instant.now();
    }

    private void validateStateTransition(VariantLifecycleState from, VariantLifecycleState to) {
        switch (from) {
            case SKU_DRAFT -> {
                if (to != VariantLifecycleState.SKU_IN_REVIEW && to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_IN_REVIEW -> {
                if (to != VariantLifecycleState.SKU_ACTIVE && to != VariantLifecycleState.SKU_DRAFT && to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_ACTIVE -> {
                if (to != VariantLifecycleState.SKU_RESTRICTED &&
                    to != VariantLifecycleState.SKU_SUSPENDED &&
                    to != VariantLifecycleState.SKU_RECALLED_LOT_HOLD &&
                    to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_RESTRICTED -> {
                if (to != VariantLifecycleState.SKU_IN_REVIEW &&
                    to != VariantLifecycleState.SKU_ACTIVE &&
                    to != VariantLifecycleState.SKU_SUSPENDED &&
                    to != VariantLifecycleState.SKU_RECALLED_LOT_HOLD &&
                    to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_SUSPENDED -> {
                if (to != VariantLifecycleState.SKU_ACTIVE &&
                    to != VariantLifecycleState.SKU_RECALLED_LOT_HOLD &&
                    to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_RECALLED_LOT_HOLD -> {
                if (to != VariantLifecycleState.SKU_ACTIVE &&
                    to != VariantLifecycleState.SKU_IN_REVIEW &&
                    to != VariantLifecycleState.SKU_DISCONTINUED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_DISCONTINUED -> {
                if (to != VariantLifecycleState.SKU_ARCHIVED) {
                    throwInvalidTransition(from, to);
                }
            }
            case SKU_ARCHIVED -> throw new DomainException(String.format("Variant [%s] is ARCHIVED and cannot transition to [%s]", this.sku, to));
        }
    }

    private void throwInvalidTransition(VariantLifecycleState from, VariantLifecycleState to) {
        throw new DomainException(String.format("Invalid variant FSM transition for SKU [%s]: %s -> %s", this.sku, from, to));
    }

    public void addSuspensionReason(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.activeSuspensionReasons.add(reason.trim());
            this.updatedAt = Instant.now();
        }
    }

    public void removeSuspensionReason(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.activeSuspensionReasons.remove(reason.trim());
            this.updatedAt = Instant.now();
        }
    }

    public void addSuspendedComponent(String componentSku) {
        if (componentSku != null && !componentSku.isBlank()) {
            this.suspendedComponents.add(componentSku.trim());
            this.updatedAt = Instant.now();
        }
    }

    public void removeSuspendedComponent(String componentSku) {
        if (componentSku != null && !componentSku.isBlank()) {
            this.suspendedComponents.remove(componentSku.trim());
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

    public void applyAttributes(Map<String, AttributeValue> newAttributes) {
        if (newAttributes != null && !newAttributes.isEmpty()) {
            this.attributes.putAll(newAttributes);
            this.updatedAt = Instant.now();
        }
    }

    public void markDiscontinued() {
        this.availabilityStatus = VariantAvailabilityStatus.DISCONTINUED;
        this.lifecycleState = VariantLifecycleState.SKU_DISCONTINUED;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.lifecycleState = VariantLifecycleState.SKU_ACTIVE;
        evaluateAvailability();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void incrementPublishedVersion() {
        this.publishedVersion++;
        this.updatedAt = Instant.now();
    }

    // ==========================================================================
    // GETTERS & SETTERS
    // ==========================================================================

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
            this.updatedAt = Instant.now();
        }
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

    public VariantLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(VariantLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public Map<String, String> getMarketComplianceMap() {
        return Collections.unmodifiableMap(marketComplianceMap);
    }

    public Map<String, String> getChannelStatusMap() {
        return Collections.unmodifiableMap(channelStatusMap);
    }

    public Set<String> getActiveSuspensionReasons() {
        return Collections.unmodifiableSet(activeSuspensionReasons);
    }

    public Set<String> getSuspendedComponents() {
        return Collections.unmodifiableSet(suspendedComponents);
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
        return isBestseller;
    }

    public void setBestseller(boolean bestseller) {
        this.isBestseller = bestseller;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
