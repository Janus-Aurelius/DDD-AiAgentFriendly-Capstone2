package com.retail.pim.dam.domain.valueobjects;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Value Object governing Digital Rights Management (DRM) and commercial licensing.
 */
public final class DRMLicense implements Serializable {

    private final String licenseHolder;
    private final Instant expirationDate;
    private final String usageRights;
    private final boolean royaltyFree;

    public DRMLicense(String licenseHolder, Instant expirationDate, String usageRights, boolean royaltyFree) {
        if (licenseHolder == null || licenseHolder.isBlank()) {
            throw new IllegalArgumentException("License holder is required");
        }
        this.licenseHolder = licenseHolder.trim();
        this.expirationDate = expirationDate;
        this.usageRights = (usageRights != null) ? usageRights.trim() : "All Channels Worldwide";
        this.royaltyFree = royaltyFree;
    }

    public static DRMLicense perpetual(String licenseHolder) {
        return new DRMLicense(licenseHolder, null, "Perpetual Worldwide Rights", true);
    }

    public boolean isExpired() {
        if (expirationDate == null) {
            return false; // Perpetual license
        }
        return Instant.now().isAfter(expirationDate);
    }

    public String getLicenseHolder() {
        return licenseHolder;
    }

    public Instant getExpirationDate() {
        return expirationDate;
    }

    public String getUsageRights() {
        return usageRights;
    }

    public boolean isRoyaltyFree() {
        return royaltyFree;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DRMLicense that = (DRMLicense) o;
        return royaltyFree == that.royaltyFree &&
               Objects.equals(licenseHolder, that.licenseHolder) &&
               Objects.equals(expirationDate, that.expirationDate) &&
               Objects.equals(usageRights, that.usageRights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licenseHolder, expirationDate, usageRights, royaltyFree);
    }
}
