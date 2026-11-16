package com.retail.pim.dam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "digital_asset", schema = "dam")
public class DigitalAssetJpaEntity {

    @Id
    private UUID id;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "raw_s3_url", nullable = false, columnDefinition = "TEXT")
    private String rawS3Url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "license", nullable = false, columnDefinition = "jsonb")
    private String licenseJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "renditions", nullable = false, columnDefinition = "jsonb")
    private String renditionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_product_ids", nullable = false, columnDefinition = "jsonb")
    private String linkedProductIdsJson;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DigitalAssetJpaEntity() {}

    public DigitalAssetJpaEntity(UUID id, String originalFileName, String title, String rawS3Url,
                                 String metadataJson, String licenseJson, String renditionsJson,
                                 String linkedProductIdsJson, boolean active,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.originalFileName = originalFileName;
        this.title = title;
        this.rawS3Url = rawS3Url;
        this.metadataJson = metadataJson;
        this.licenseJson = licenseJson;
        this.renditionsJson = renditionsJson;
        this.linkedProductIdsJson = linkedProductIdsJson;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRawS3Url() { return rawS3Url; }
    public void setRawS3Url(String rawS3Url) { this.rawS3Url = rawS3Url; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getLicenseJson() { return licenseJson; }
    public void setLicenseJson(String licenseJson) { this.licenseJson = licenseJson; }

    public String getRenditionsJson() { return renditionsJson; }
    public void setRenditionsJson(String renditionsJson) { this.renditionsJson = renditionsJson; }

    public String getLinkedProductIdsJson() { return linkedProductIdsJson; }
    public void setLinkedProductIdsJson(String linkedProductIdsJson) { this.linkedProductIdsJson = linkedProductIdsJson; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
