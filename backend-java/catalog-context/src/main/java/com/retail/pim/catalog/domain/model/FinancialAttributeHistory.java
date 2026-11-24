package com.retail.pim.catalog.domain.model;

import com.retail.pim.shared.domain.Entity;
import com.retail.pim.shared.exceptions.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bi-Temporal Historical Entity tracking changes to physical dimensions,
 * weight, tax classifications, and customs codes across Unit-of-Measure (UOM) tiers (CUJ 7).
 */
public class FinancialAttributeHistory extends Entity<UUID> {

    public static final Instant INFINITY = Instant.parse("9999-12-31T23:59:59.999999Z");

    private final String sku;
    private final String uomTier;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal grossWeightKg;
    private String taxBand;
    private String customsHsnCode;
    private final Instant effectiveFrom;
    private Instant effectiveTo;
    private final Instant createdAt;

    public FinancialAttributeHistory(UUID id, String sku, String uomTier,
                                     BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                                     BigDecimal grossWeightKg, String taxBand, String customsHsnCode,
                                     Instant effectiveFrom, Instant effectiveTo, Instant createdAt) {
        super(id != null ? id : UUID.randomUUID());

        if (sku == null || sku.isBlank()) {
            throw new DomainException("SKU cannot be empty");
        }
        if (uomTier == null || uomTier.isBlank()) {
            throw new DomainException("UOM Tier cannot be empty");
        }
        if (lengthCm == null || lengthCm.compareTo(BigDecimal.ZERO) <= 0 ||
            widthCm == null || widthCm.compareTo(BigDecimal.ZERO) <= 0 ||
            heightCm == null || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Dimensions (length, width, height) must be positive values");
        }
        if (grossWeightKg == null || grossWeightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Gross weight must be a positive value");
        }
        if (taxBand == null || taxBand.isBlank()) {
            throw new DomainException("Tax band cannot be empty");
        }
        if (customsHsnCode == null || customsHsnCode.isBlank()) {
            throw new DomainException("Customs HSN code cannot be empty");
        }

        this.sku = sku.trim().toUpperCase();
        this.uomTier = uomTier.trim().toUpperCase();
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.grossWeightKg = grossWeightKg;
        this.taxBand = taxBand.trim().toUpperCase();
        this.customsHsnCode = customsHsnCode.trim().toUpperCase();
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : Instant.now();
        this.effectiveTo = effectiveTo != null ? effectiveTo : INFINITY;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public FinancialAttributeHistory(UUID id, String sku, String uomTier,
                                     BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                                     BigDecimal grossWeightKg, String taxBand, String customsHsnCode,
                                     Instant effectiveFrom) {
        this(id, sku, uomTier, lengthCm, widthCm, heightCm, grossWeightKg, taxBand, customsHsnCode,
                effectiveFrom, INFINITY, Instant.now());
    }

    /**
     * Temporal predicate: Checks whether this historical snapshot was active as of the given query timestamp.
     */
    public boolean isEffectiveAt(Instant asOf) {
        if (asOf == null) {
            return false;
        }
        return (!asOf.isBefore(effectiveFrom)) && (effectiveTo == null || asOf.isBefore(effectiveTo));
    }

    /**
     * Bi-temporal close-and-insert: Closes the current effective period when an attribute update is recorded.
     */
    public void closeEffectivePeriod(Instant closedAt) {
        if (closedAt == null) {
            throw new DomainException("Close timestamp cannot be null");
        }
        if (closedAt.isBefore(this.effectiveFrom)) {
            throw new DomainException("Close timestamp cannot be before effectiveFrom timestamp");
        }
        this.effectiveTo = closedAt;
    }

    public String getSku() {
        return sku;
    }

    public String getUomTier() {
        return uomTier;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public BigDecimal getGrossWeightKg() {
        return grossWeightKg;
    }

    public String getTaxBand() {
        return taxBand;
    }

    public String getCustomsHsnCode() {
        return customsHsnCode;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
