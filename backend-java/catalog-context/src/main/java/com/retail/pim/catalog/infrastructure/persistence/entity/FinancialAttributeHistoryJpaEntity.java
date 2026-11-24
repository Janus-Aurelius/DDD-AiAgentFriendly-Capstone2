package com.retail.pim.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Persistence Entity mapping to table `catalog.financial_attribute_history`.
 */
@Entity
@Table(name = "financial_attribute_history", schema = "catalog")
public class FinancialAttributeHistoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "uom_tier", nullable = false, length = 50)
    private String uomTier;

    @Column(name = "length_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "gross_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal grossWeightKg;

    @Column(name = "tax_band", nullable = false, length = 50)
    private String taxBand;

    @Column(name = "customs_hsn_code", nullable = false, length = 50)
    private String customsHsnCode;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FinancialAttributeHistoryJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getUomTier() {
        return uomTier;
    }

    public void setUomTier(String uomTier) {
        this.uomTier = uomTier;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getGrossWeightKg() {
        return grossWeightKg;
    }

    public void setGrossWeightKg(BigDecimal grossWeightKg) {
        this.grossWeightKg = grossWeightKg;
    }

    public String getTaxBand() {
        return taxBand;
    }

    public void setTaxBand(String taxBand) {
        this.taxBand = taxBand;
    }

    public String getCustomsHsnCode() {
        return customsHsnCode;
    }

    public void setCustomsHsnCode(String customsHsnCode) {
        this.customsHsnCode = customsHsnCode;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
