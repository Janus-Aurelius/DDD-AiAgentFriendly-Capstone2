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
 *
 * Hardened with:
 * 1. Multi-lifecycle readiness gating across PIM, ERP, and OMS (CUJ 5).
 * 2. Monotonic sequence ordering discarding out-of-sequence events.
 * 3. Dynamic embargo unmasking predicates.
 */
public class ChannelItemProjection extends Entity<UUID> {

    private UUID globalProductId;
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

    // Multi-lifecycle readiness state (CUJ 5)
    private ReadinessStatus readinessStatus = ReadinessStatus.UNINDEXED_SKELETON;
    private boolean pimActive;
    private boolean priceInitialized;
    private boolean stockInitialized;
    private Integer availableStock = 0;
    private Instant embargoUntil;
    private Instant streetDate;

    // Monotonic event sequence version tracking
    private long pimLastProcessedVersion = -1L;
    private long erpLastProcessedVersion = -1L;
    private long omsLastProcessedVersion = -1L;

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

        // Standard constructor defaults: initialized if title and price are supplied
        this.pimActive = (title != null && !title.isBlank());
        this.priceInitialized = (price != null);
        this.stockInitialized = true;
        evaluateReadiness();
    }

    /**
     * Factory for creating an unindexed skeleton projection when OMS stock or ERP price arrives
     * before PIM master data enrichment (CUJ 5).
     */
    public static ChannelItemProjection createSkeleton(String sku, String regionCode) {
        ChannelItemProjection skeleton = new ChannelItemProjection(
                UUID.randomUUID(),
                null,
                sku,
                null,
                null,
                "USD",
                regionCode != null ? regionCode : "US"
        );
        skeleton.pimActive = false;
        skeleton.priceInitialized = false;
        skeleton.stockInitialized = false;
        skeleton.availableStock = 0;
        skeleton.readinessStatus = ReadinessStatus.UNINDEXED_SKELETON;
        return skeleton;
    }

    /**
     * Evaluates whether the projection meets all three lifecycle readiness prerequisites:
     * PIM Active == true, Price Initialized == true, and Stock Initialized == true.
     */
    public void evaluateReadiness() {
        if (this.pimActive && this.priceInitialized && this.stockInitialized) {
            this.readinessStatus = ReadinessStatus.READY_INDEXED;
        } else {
            this.readinessStatus = ReadinessStatus.UNINDEXED_SKELETON;
        }
    }

    /**
     * Dynamic Storefront Predicate:
     * Product is discoverable in public search if and only if it is READY_INDEXED and
     * the current timestamp is at or past the embargo schedule (asOf >= embargoUntil).
     */
    public boolean isDiscoverable(Instant asOf) {
        if (this.readinessStatus != ReadinessStatus.READY_INDEXED) {
            return false;
        }
        if (this.embargoUntil != null && asOf.isBefore(this.embargoUntil)) {
            return false;
        }
        return true;
    }

    /**
     * Monotonic handler for PIM master data events.
     */
    public boolean applyPimMasterData(UUID globalProductId, String title, String categoryPath,
                                      Instant embargoUntil, Instant streetDate, boolean active, long version) {
        if (version <= this.pimLastProcessedVersion && this.pimLastProcessedVersion != -1L) {
            return false;
        }
        this.globalProductId = globalProductId;
        this.title = title;
        this.categoryPath = categoryPath;
        this.embargoUntil = embargoUntil;
        this.streetDate = streetDate;
        this.pimActive = active;
        this.pimLastProcessedVersion = version;
        this.lastUpdated = Instant.now();
        evaluateReadiness();
        return true;
    }

    /**
     * Monotonic handler for ERP pricing events.
     */
    public boolean applyErpPrice(BigDecimal price, String currency, long version) {
        if (version <= this.erpLastProcessedVersion && this.erpLastProcessedVersion != -1L) {
            return false;
        }
        this.price = price;
        if (currency != null && !currency.isBlank()) {
            this.currency = currency;
        }
        this.priceInitialized = (price != null);
        this.erpLastProcessedVersion = version;
        this.lastUpdated = Instant.now();
        evaluateReadiness();
        return true;
    }

    /**
     * Monotonic handler for OMS inventory events.
     */
    public boolean applyOmsStock(int availableStock, long version) {
        if (version <= this.omsLastProcessedVersion && this.omsLastProcessedVersion != -1L) {
            return false;
        }
        this.availableStock = availableStock;
        this.stockInitialized = true;
        this.omsLastProcessedVersion = version;
        this.lastUpdated = Instant.now();
        evaluateReadiness();
        return true;
    }

    public void updateContent(String title, String descriptionHtml, String plainTextDescription, String categoryPath) {
        this.title = title;
        this.descriptionHtml = descriptionHtml;
        this.plainTextDescription = plainTextDescription;
        this.categoryPath = categoryPath;
        this.pimActive = (title != null && !title.isBlank());
        this.lastUpdated = Instant.now();
        evaluateReadiness();
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

    public ReadinessStatus getReadinessStatus() {
        return readinessStatus;
    }

    public boolean isPimActive() {
        return pimActive;
    }

    public boolean isPriceInitialized() {
        return priceInitialized;
    }

    public boolean isStockInitialized() {
        return stockInitialized;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
        this.stockInitialized = (availableStock != null);
        this.lastUpdated = Instant.now();
        evaluateReadiness();
    }

    public Instant getEmbargoUntil() {
        return embargoUntil;
    }

    public void setEmbargoUntil(Instant embargoUntil) {
        this.embargoUntil = embargoUntil;
        this.lastUpdated = Instant.now();
    }

    public Instant getStreetDate() {
        return streetDate;
    }

    public void setStreetDate(Instant streetDate) {
        this.streetDate = streetDate;
        this.lastUpdated = Instant.now();
    }

    public long getPimLastProcessedVersion() {
        return pimLastProcessedVersion;
    }

    public long getErpLastProcessedVersion() {
        return erpLastProcessedVersion;
    }

    public long getOmsLastProcessedVersion() {
        return omsLastProcessedVersion;
    }

    /**
     * Summary Projection DTO:
     * Lightweight payload designed for sub-5ms search result list views and mobile browse grids.
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
            String regionCode,
            ReadinessStatus readinessStatus,
            Instant embargoUntil,
            Integer availableStock
    ) {
        public SummaryProjection(UUID id, UUID globalProductId, String sku, String title,
                                 BigDecimal price, String currency, String primaryImageUrl,
                                 String categoryPath, String regionCode) {
            this(id, globalProductId, sku, title, price, currency, primaryImageUrl,
                    categoryPath, regionCode, ReadinessStatus.READY_INDEXED, null, 0);
        }
    }

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
            Instant lastUpdated,
            ReadinessStatus readinessStatus,
            Instant embargoUntil,
            Integer availableStock
    ) {
        public DetailProjection(UUID id, UUID globalProductId, String sku, String title,
                                String descriptionHtml, String plainTextDescription, String categoryPath,
                                BigDecimal price, String currency, String primaryImageUrl,
                                List<String> galleryImageUrls, String regionCode,
                                boolean complianceCleared, Map<String, Object> attributes,
                                Instant lastUpdated) {
            this(id, globalProductId, sku, title, descriptionHtml, plainTextDescription, categoryPath,
                    price, currency, primaryImageUrl, galleryImageUrls, regionCode, complianceCleared,
                    attributes, lastUpdated, ReadinessStatus.READY_INDEXED, null, 0);
        }
    }

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
                this.regionCode,
                this.readinessStatus,
                this.embargoUntil,
                this.availableStock
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
                this.lastUpdated,
                this.readinessStatus,
                this.embargoUntil,
                this.availableStock
        );
    }
}
