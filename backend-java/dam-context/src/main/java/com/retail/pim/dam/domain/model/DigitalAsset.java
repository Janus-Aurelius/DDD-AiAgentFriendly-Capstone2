package com.retail.pim.dam.domain.model;

import com.retail.pim.dam.domain.events.AssetLicenseExpired;
import com.retail.pim.dam.domain.events.AssetLinkedToProduct;
import com.retail.pim.dam.domain.events.AssetUploaded;
import com.retail.pim.dam.domain.valueobjects.AssetMetadata;
import com.retail.pim.dam.domain.valueobjects.DRMLicense;
import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DigitalAsset: The Aggregate Root of the Creative DAM Context.
 * Represents creative media files as a core business concept, encapsulating renditions,
 * technical EXIF metadata, and legal DRM licensing lifecycle.
 *
 * Pure Java - zero framework or persistence annotations.
 */
public class DigitalAsset extends AggregateRoot<UUID> {

    private final String originalFileName;
    private String title;
    private final String rawS3Url;
    private AssetMetadata metadata;
    private DRMLicense license;
    private final List<Rendition> renditions = new ArrayList<>();
    private final Set<UUID> linkedProductIds = new HashSet<>();
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public DigitalAsset(UUID id, String originalFileName, String title, String rawS3Url,
                        AssetMetadata metadata, DRMLicense license, boolean active) {
        super(id != null ? id : UUID.randomUUID());

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new DomainException("Asset original file name is required");
        }
        if (rawS3Url == null || rawS3Url.isBlank()) {
            throw new DomainException("Raw S3 URL is required");
        }

        this.originalFileName = originalFileName.trim();
        this.title = (title != null && !title.isBlank()) ? title.trim() : originalFileName;
        this.rawS3Url = rawS3Url.trim();
        this.metadata = metadata;
        this.license = license != null ? license : DRMLicense.perpetual("Internal Corporate Rights");
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static DigitalAsset create(String originalFileName, String title, String rawS3Url,
                                      long fileSizeBytes, String mimeType, DRMLicense license) {
        DigitalAsset asset = new DigitalAsset(
                UUID.randomUUID(),
                originalFileName,
                title,
                rawS3Url,
                null,
                license,
                true
        );

        asset.registerEvent(new AssetUploaded(
                asset.getId(),
                originalFileName,
                mimeType,
                fileSizeBytes,
                rawS3Url
        ));

        return asset;
    }

    public void setMetadata(AssetMetadata metadata) {
        if (metadata == null) {
            throw new DomainException("Asset metadata cannot be null");
        }
        this.metadata = metadata;
        this.updatedAt = Instant.now();
    }

    public void addRendition(Rendition rendition) {
        if (rendition == null) {
            throw new DomainException("Rendition cannot be null");
        }
        this.renditions.add(rendition);
        this.updatedAt = Instant.now();
    }

    public void linkToProduct(UUID productId, String sku, boolean isPrimaryImage) {
        if (productId == null) {
            throw new DomainException("Product ID cannot be null");
        }
        if (license != null && license.isExpired()) {
            throw new DomainException(String.format(
                    "Cannot link asset [%s] to product [%s]: DRM license has expired",
                    this.getId(), productId));
        }

        this.linkedProductIds.add(productId);
        this.updatedAt = Instant.now();

        registerEvent(new AssetLinkedToProduct(this.getId(), productId, sku, isPrimaryImage));
    }

    public void unlinkFromProduct(UUID productId) {
        if (productId != null && this.linkedProductIds.remove(productId)) {
            this.updatedAt = Instant.now();
        }
    }

    /**
     * The Rule/Lock: Evaluates DRM license expiration.
     * If expired, deactivates asset and registers AssetLicenseExpired event for Core Catalog.
     */
    public boolean checkDrmExpiration() {
        if (license != null && license.isExpired() && this.active) {
            this.active = false;
            this.updatedAt = Instant.now();

            registerEvent(new AssetLicenseExpired(
                    this.getId(),
                    license.getLicenseHolder(),
                    license.getExpirationDate(),
                    new HashSet<>(this.linkedProductIds)
            ));
            return true;
        }
        return false;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getTitle() {
        return title;
    }

    public String getRawS3Url() {
        return rawS3Url;
    }

    public AssetMetadata getMetadata() {
        return metadata;
    }

    public DRMLicense getLicense() {
        return license;
    }

    public List<Rendition> getRenditions() {
        return Collections.unmodifiableList(renditions);
    }

    public Set<UUID> getLinkedProductIds() {
        return Collections.unmodifiableSet(linkedProductIds);
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
