package com.retail.pim.localization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "regional_catalog_item",
        schema = "localization",
        uniqueConstraints = @UniqueConstraint(name = "uq_regional_item", columnNames = {"global_product_id", "region_code"})
)
public class RegionalCatalogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "global_product_id", nullable = false)
    private UUID globalProductId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overrides", nullable = false, columnDefinition = "jsonb")
    private String overridesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "translations", nullable = false, columnDefinition = "jsonb")
    private String translationsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RegionalCatalogJpaEntity() {}

    public RegionalCatalogJpaEntity(UUID id, UUID globalProductId, String productCode, String regionCode,
                                    String status, String overridesJson, String translationsJson,
                                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.globalProductId = globalProductId;
        this.productCode = productCode;
        this.regionCode = regionCode;
        this.status = status;
        this.overridesJson = overridesJson;
        this.translationsJson = translationsJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getGlobalProductId() { return globalProductId; }
    public void setGlobalProductId(UUID globalProductId) { this.globalProductId = globalProductId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOverridesJson() { return overridesJson; }
    public void setOverridesJson(String overridesJson) { this.overridesJson = overridesJson; }

    public String getTranslationsJson() { return translationsJson; }
    public void setTranslationsJson(String translationsJson) { this.translationsJson = translationsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
