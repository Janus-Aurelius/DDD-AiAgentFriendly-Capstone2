package com.retail.pim.localization.domain.model;

import com.retail.pim.shared.domain.Entity;

import java.util.UUID;

/**
 * Entity: Represents a regional override for a specific attribute
 * (e.g. overriding global "Weight: 50lbs" with regional metric "Weight: 22.6kg").
 */
public class RegionalOverride extends Entity<UUID> {

    private final String attributeKey;
    private final String globalDefaultValue;
    private String regionalValue;
    private final String reason;

    public RegionalOverride(UUID id, String attributeKey, String globalDefaultValue, String regionalValue, String reason) {
        super(id != null ? id : UUID.randomUUID());
        if (attributeKey == null || attributeKey.isBlank()) {
            throw new IllegalArgumentException("Attribute key cannot be blank");
        }
        if (regionalValue == null || regionalValue.isBlank()) {
            throw new IllegalArgumentException("Regional override value cannot be blank");
        }
        this.attributeKey = attributeKey.trim();
        this.globalDefaultValue = globalDefaultValue;
        this.regionalValue = regionalValue.trim();
        this.reason = (reason != null) ? reason.trim() : "Regional Standard Alignment";
    }

    public void updateValue(String newValue) {
        if (newValue == null || newValue.isBlank()) {
            throw new IllegalArgumentException("New regional value cannot be blank");
        }
        this.regionalValue = newValue.trim();
    }

    public String getAttributeKey() {
        return attributeKey;
    }

    public String getGlobalDefaultValue() {
        return globalDefaultValue;
    }

    public String getRegionalValue() {
        return regionalValue;
    }

    public String getReason() {
        return reason;
    }
}
