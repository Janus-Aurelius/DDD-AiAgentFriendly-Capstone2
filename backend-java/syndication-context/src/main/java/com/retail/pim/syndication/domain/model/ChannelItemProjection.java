package com.retail.pim.syndication.domain.model;

import com.retail.pim.shared.domain.Entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS Read Model Projection: A massively flattened, denormalized document representing one product.
 * Fully prepared for single sub-10ms queries by Elasticsearch, Storefronts, or Amazon feeds.
 */
public class ChannelItemProjection extends Entity<UUID> {

    private final UUID globalProductId;
    private final String sku;
    private String title;
    private String descriptionHtml;
    private String plainTextDescription;
    private String categoryPath;
    private BigDecimal price;
    private String currency;
    private String primaryImageUrl;
    private List<String> galleryImageUrls = List.of();
    private String regionCode;
    private boolean complianceCleared;
    private final Map<String, Object> flattenedAttributes = new HashMap<>();
    private Instant lastUpdated;

    public ChannelItemProjection(UUID id, UUID globalProductId, String sku, String title,
                                 BigDecimal price, String currency, String regionCode) {
        super(id != null ? id : UUID.randomUUID());
        this.globalProductId = globalProductId;
        this.sku = sku;
        this.title = title;
        this.price = price;
        this.currency = currency != null ? currency : "USD";
        this.regionCode = regionCode != null ? regionCode : "US";
        this.lastUpdated = Instant.now();
    }

    public void updateContent(String title, String descriptionHtml, String plainTextDescription, String categoryPath) {
        this.title = title;
        this.descriptionHtml = descriptionHtml;
        this.plainTextDescription = plainTextDescription;
        this.categoryPath = categoryPath;
        this.lastUpdated = Instant.now();
    }

    public void updateImages(String primaryImageUrl, List<String> galleryImageUrls) {
        this.primaryImageUrl = primaryImageUrl;
        if (galleryImageUrls != null) {
            this.galleryImageUrls = List.copyOf(galleryImageUrls);
        }
        this.lastUpdated = Instant.now();
    }

    public void setComplianceCleared(boolean cleared) {
        this.complianceCleared = cleared;
        this.lastUpdated = Instant.now();
    }

    public void putAttribute(String key, Object val) {
        this.flattenedAttributes.put(key, val);
        this.lastUpdated = Instant.now();
    }

    public UUID getGlobalProductId() {
        return globalProductId;
    }

    public String getSku() {
        return sku;
    }

    public String getTitle() {
        return title;
    }

    public String getDescriptionHtml() {
        return descriptionHtml;
    }

    public String getPlainTextDescription() {
        return plainTextDescription;
    }

    public String getCategoryPath() {
        return categoryPath;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPrimaryImageUrl() {
        return primaryImageUrl;
    }

    public List<String> getGalleryImageUrls() {
        return galleryImageUrls;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public boolean isComplianceCleared() {
        return complianceCleared;
    }

    public Map<String, Object> getFlattenedAttributes() {
        return Collections.unmodifiableMap(flattenedAttributes);
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Summary Projection DTO:
     * Lightweight payload designed for sub-5ms search result list views and mobile browse grids.
     * Prevents multi-megabyte denormalized payload explosion.
     */
    public record SummaryProjection(
            UUID id,
            UUID globalProductId,
            String sku,
            String title,
            BigDecimal price,
            String currency,
            String primaryImageUrl,
            String categoryPath,
            String regionCode
    ) {}

    /**
     * Detail Projection DTO:
     * Full Product Detail Page (PDP) payload containing all media renditions, rich HTML marketing blocks,
     * and technical attribute specifications.
     */
    public record DetailProjection(
            UUID id,
            UUID globalProductId,
            String sku,
            String title,
            String descriptionHtml,
            String plainTextDescription,
            String categoryPath,
            BigDecimal price,
            String currency,
            String primaryImageUrl,
            List<String> galleryImageUrls,
            String regionCode,
            boolean complianceCleared,
            Map<String, Object> attributes,
            Instant lastUpdated
    ) {}

    public SummaryProjection toSummaryProjection() {
        return new SummaryProjection(
                this.getId(),
                this.globalProductId,
                this.sku,
                this.title,
                this.price,
                this.currency,
                this.primaryImageUrl,
                this.categoryPath,
                this.regionCode
        );
    }

    public DetailProjection toDetailProjection() {
        return new DetailProjection(
                this.getId(),
                this.globalProductId,
                this.sku,
                this.title,
                this.descriptionHtml,
                this.plainTextDescription,
                this.categoryPath,
                this.price,
                this.currency,
                this.primaryImageUrl,
                this.galleryImageUrls,
                this.regionCode,
                this.complianceCleared,
                Collections.unmodifiableMap(this.flattenedAttributes),
                this.lastUpdated
        );
    }
}
