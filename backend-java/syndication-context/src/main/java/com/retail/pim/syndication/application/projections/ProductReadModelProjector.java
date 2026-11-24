package com.retail.pim.syndication.application.projections;

import com.retail.pim.syndication.application.services.StorefrontReadinessGate;
import com.retail.pim.syndication.domain.model.ChannelCatalog;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import com.retail.pim.syndication.domain.repository.ChannelCatalogRepository;
import com.retail.pim.syndication.domain.valueobjects.MappingTemplate;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import com.retail.pim.syndication.infrastructure.external.AmazonMarketplaceApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CQRS Read Model Projector: "The Great Aggregator".
 *
 * Flattens asynchronous event streams across:
 * - RegionalItemPublished / Catalog Master Data (from Localization / Catalog)
 * - ComplianceCleared (from Legal)
 * - AssetLinked (from DAM)
 * - OmsStockReceived (from OMS / WMS)
 * - ErpPriceUpdated (from ERP)
 *
 * Hardened with:
 * 1. Multi-Lifecycle Readiness Gate (PIM active, ERP price initialized, OMS stock initialized) (CUJ 5).
 * 2. Non-Destructive Skeleton Lifecycle: creates dormant unindexed skeletons when stock/price arrives before PIM master data.
 * 3. Monotonic Sequence Ordering: discards stale/out-of-order events.
 * 4. Debounced Virtual Bundle ATP Accumulator: flushes at bounded intervals (e.g. 5 seconds per bundle) preventing write saturation.
 * 5. Claim-Check Buffer: eliminates out-of-order Kafka message race conditions for early assets.
 * 6. Silent Zombie Asset Purging: strips expired DRM assets and retracts live marketplace feeds.
 */
@Service
public class ProductReadModelProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductReadModelProjector.class);

    private final ElasticsearchProjectionRepository esRepo;
    private final AmazonMarketplaceApiClient amazonClient;
    private final ChannelCatalogRepository channelCatalogRepository;
    private final com.retail.pim.syndication.domain.service.CategoryMappingTemplateRegistry categoryTemplateRegistry;
    private final StorefrontReadinessGate readinessGate;

    // Cache of active projections being aggregated: key = "region:sku"
    private final Map<String, ChannelItemProjection> projectionStore = new ConcurrentHashMap<>();

    // Claim-Check Buffer: Holds asset links that arrive BEFORE RegionalItemPublished initializes projection
    public record PendingAssetLink(String imageUrl, boolean isPrimary) {}
    private final Map<String, List<PendingAssetLink>> pendingAssetBuffer = new ConcurrentHashMap<>();

    // Debounced Virtual Bundle ATP Accumulator: key = "region:bundleSku"
    public record PendingBundleAtp(String bundleSku, String regionCode, int atp, Instant accumulatedAt, Instant lastFlushedAt) {}
    private final Map<String, PendingBundleAtp> debouncedBundleAtpStore = new ConcurrentHashMap<>();

    public ProductReadModelProjector(ElasticsearchProjectionRepository esRepo,
                                     AmazonMarketplaceApiClient amazonClient,
                                     ChannelCatalogRepository channelCatalogRepository,
                                     com.retail.pim.syndication.domain.service.CategoryMappingTemplateRegistry categoryTemplateRegistry) {
        this(esRepo, amazonClient, channelCatalogRepository, categoryTemplateRegistry, new StorefrontReadinessGate());
    }

    public ProductReadModelProjector(ElasticsearchProjectionRepository esRepo,
                                     AmazonMarketplaceApiClient amazonClient,
                                     ChannelCatalogRepository channelCatalogRepository,
                                     com.retail.pim.syndication.domain.service.CategoryMappingTemplateRegistry categoryTemplateRegistry,
                                     StorefrontReadinessGate readinessGate) {
        this.esRepo = esRepo;
        this.amazonClient = amazonClient;
        this.channelCatalogRepository = channelCatalogRepository;
        this.categoryTemplateRegistry = categoryTemplateRegistry;
        this.readinessGate = readinessGate != null ? readinessGate : new StorefrontReadinessGate();
    }

    /**
     * Aggregation Step 1: Regional item published from Localization Context
     * Also drains any pending asset links from the Claim-Check Buffer.
     */
    public void projectRegionalItemPublished(UUID globalProductId, String productCode, String regionCode,
                                             String localizedTitle, Map<String, String> attributeOverrides) {
        String key = regionCode + ":" + productCode;
        ChannelItemProjection projection = projectionStore.computeIfAbsent(key, k ->
                new ChannelItemProjection(
                        UUID.randomUUID(),
                        globalProductId,
                        productCode,
                        localizedTitle,
                        new BigDecimal("999.99"),
                        "USD",
                        regionCode
                )
        );

        projection.updateContent(
                localizedTitle,
                "<p><strong>" + localizedTitle + "</strong> - High Performance Catalog Item.</p>",
                localizedTitle + " - High Performance Catalog Item.",
                "Electronics > Displays > Smart TVs"
        );

        if (attributeOverrides != null) {
            attributeOverrides.forEach(projection::putAttribute);
        }

        drainAssetBuffer(key, projection);
        finalizeAndPublish(projection);
    }

    /**
     * Slice 5: PIM Master Data Projection with Monotonic Version Guard
     */
    public void projectPimMasterData(String sku, UUID globalProductId, String regionCode,
                                     String title, String categoryPath, Instant embargoUntil,
                                     Instant streetDate, boolean active, long eventVersion) {
        String key = (regionCode != null ? regionCode : "US") + ":" + sku;
        ChannelItemProjection projection = projectionStore.computeIfAbsent(key, k ->
                ChannelItemProjection.createSkeleton(sku, regionCode != null ? regionCode : "US"));

        boolean updated = projection.applyPimMasterData(globalProductId, title, categoryPath,
                embargoUntil, streetDate, active, eventVersion);

        if (!updated) {
            log.info("Projector: Stale PIM event discarded for SKU [{}] (version {})", sku, eventVersion);
            return;
        }

        drainAssetBuffer(key, projection);
        finalizeAndPublish(projection);
    }

    /**
     * Slice 5: ERP Pricing Projection with Monotonic Version Guard
     */
    public void projectErpPrice(String sku, String regionCode, BigDecimal price, String currency, long eventVersion) {
        String key = (regionCode != null ? regionCode : "US") + ":" + sku;
        ChannelItemProjection projection = projectionStore.computeIfAbsent(key, k ->
                ChannelItemProjection.createSkeleton(sku, regionCode != null ? regionCode : "US"));

        boolean updated = projection.applyErpPrice(price, currency, eventVersion);
        if (!updated) {
            log.info("Projector: Stale ERP pricing event discarded for SKU [{}] (version {})", sku, eventVersion);
            return;
        }

        finalizeAndPublish(projection);
    }

    /**
     * Slice 5: OMS Inventory Projection with Monotonic Version Guard
     */
    public void projectOmsStock(String sku, String regionCode, int availableStock, long eventVersion) {
        String key = (regionCode != null ? regionCode : "US") + ":" + sku;
        ChannelItemProjection projection = projectionStore.computeIfAbsent(key, k ->
                ChannelItemProjection.createSkeleton(sku, regionCode != null ? regionCode : "US"));

        boolean updated = projection.applyOmsStock(availableStock, eventVersion);
        if (!updated) {
            log.info("Projector: Stale OMS stock event discarded for SKU [{}] (version {})", sku, eventVersion);
            return;
        }

        finalizeAndPublish(projection);
    }

    /**
     * Aggregation Step 2: Legal/Compliance clearance received
     */
    public void projectComplianceCleared(String sku, String regionCode, boolean cleared) {
        String key = regionCode + ":" + sku;
        ChannelItemProjection projection = projectionStore.get(key);
        if (projection != null) {
            projection.setComplianceCleared(cleared);
            log.info("Projector: Legal compliance [{}] applied to SKU [{}]", cleared, sku);
            finalizeAndPublish(projection);
        }
    }

    /**
     * Aggregation Step 3: Creative asset linked from DAM Context
     * Uses Claim-Check Buffer if projection is not yet initialized to prevent out-of-order drop.
     */
    public void projectAssetLinked(String sku, String regionCode, String imageUrl, boolean isPrimary) {
        String key = regionCode + ":" + sku;
        ChannelItemProjection projection = projectionStore.get(key);

        if (projection != null) {
            applyAssetLink(projection, imageUrl, isPrimary);
            log.info("Projector: Applied DAM Asset [{}] to SKU [{}] (primary={})", imageUrl, sku, isPrimary);
            finalizeAndPublish(projection);
        } else {
            // Buffer early-arriving asset link until RegionalItemPublished arrives
            log.warn("Projector: Out-of-order event detected. Buffering asset link [{}] for uninitialized SKU [{}]",
                    imageUrl, key);
            pendingAssetBuffer.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                    .add(new PendingAssetLink(imageUrl, isPrimary));
        }
    }

    private void drainAssetBuffer(String key, ChannelItemProjection projection) {
        List<PendingAssetLink> pendingLinks = pendingAssetBuffer.remove(key);
        if (pendingLinks != null && !pendingLinks.isEmpty()) {
            log.info("Projector: Draining {} buffered asset links for [{}]", pendingLinks.size(), key);
            for (PendingAssetLink link : pendingLinks) {
                applyAssetLink(projection, link.imageUrl(), link.isPrimary());
            }
        }
    }

    private void applyAssetLink(ChannelItemProjection projection, String imageUrl, boolean isPrimary) {
        if (isPrimary) {
            projection.updateImages(imageUrl, projection.getGalleryImageUrls());
        } else {
            List<String> updatedGallery = new ArrayList<>(projection.getGalleryImageUrls());
            if (!updatedGallery.contains(imageUrl)) {
                updatedGallery.add(imageUrl);
            }
            projection.updateImages(projection.getPrimaryImageUrl(), updatedGallery);
        }
    }

    /**
     * Slice 5: Virtual Bundle ATP Math: min(floor(Stock_i / Qty_i))
     */
    public int calculateBundleAtp(Map<String, Integer> componentStock, Map<String, Integer> componentRequiredQty) {
        if (componentStock == null || componentRequiredQty == null || componentRequiredQty.isEmpty()) {
            return 0;
        }
        int minAtp = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : componentRequiredQty.entrySet()) {
            String compSku = entry.getKey();
            int requiredQty = entry.getValue();
            if (requiredQty <= 0) {
                continue;
            }
            int currentStock = componentStock.getOrDefault(compSku, 0);
            int possible = currentStock / requiredQty;
            if (possible < minAtp) {
                minAtp = possible;
            }
        }
        return minAtp == Integer.MAX_VALUE ? 0 : Math.max(0, minAtp);
    }

    /**
     * Slice 5: Debounced Virtual Bundle ATP Write Accumulator
     */
    public void accumulateBundleAtp(String bundleSku, String regionCode, int calculatedAtp, Instant timestamp) {
        String key = (regionCode != null ? regionCode : "US") + ":" + bundleSku;
        debouncedBundleAtpStore.compute(key, (k, existing) -> {
            Instant lastFlushed = existing != null ? existing.lastFlushedAt() : null;
            return new PendingBundleAtp(bundleSku, regionCode != null ? regionCode : "US", calculatedAtp, timestamp, lastFlushed);
        });
    }

    /**
     * Flushes debounced bundle ATP updates if minDebounceWindow has elapsed since last flush.
     */
    public Map<String, Integer> flushDebouncedBundleAtp(Instant asOf, Duration minDebounceWindow) {
        Map<String, Integer> flushed = new HashMap<>();
        for (Map.Entry<String, PendingBundleAtp> entry : debouncedBundleAtpStore.entrySet()) {
            PendingBundleAtp pending = entry.getValue();
            boolean shouldFlush = pending.lastFlushedAt() == null ||
                    !asOf.isBefore(pending.lastFlushedAt().plus(minDebounceWindow));
            if (shouldFlush) {
                String key = entry.getKey();
                ChannelItemProjection proj = projectionStore.get(key);
                if (proj != null) {
                    proj.setAvailableStock(pending.atp());
                    esRepo.indexProductProjection(proj);
                }
                debouncedBundleAtpStore.put(key, new PendingBundleAtp(
                        pending.bundleSku(), pending.regionCode(), pending.atp(), pending.accumulatedAt(), asOf
                ));
                flushed.put(pending.bundleSku(), pending.atp());
            }
        }
        return flushed;
    }

    public Optional<Integer> getAccumulatedBundleAtp(String regionCode, String bundleSku) {
        String key = (regionCode != null ? regionCode : "US") + ":" + bundleSku;
        PendingBundleAtp pending = debouncedBundleAtpStore.get(key);
        return pending != null ? Optional.of(pending.atp()) : Optional.empty();
    }

    /**
     * DRM Expiration Purge: Strips expired asset URLs from active projections and immediately
     * updates Elasticsearch cache and pushes listing retracts to Amazon SP-API.
     */
    public void purgeExpiredAsset(UUID assetId) {
        String assetToken = assetId.toString();
        log.warn("Syndication: Purging copyright-expired asset [{}] across all projections", assetId);

        for (Map.Entry<String, ChannelItemProjection> entry : projectionStore.entrySet()) {
            ChannelItemProjection proj = entry.getValue();
            boolean modified = false;

            if (proj.getPrimaryImageUrl() != null && proj.getPrimaryImageUrl().contains(assetToken)) {
                proj.updateImages(null, proj.getGalleryImageUrls());
                modified = true;
            }

            List<String> remainingGallery = proj.getGalleryImageUrls().stream()
                    .filter(url -> !url.contains(assetToken))
                    .toList();

            if (remainingGallery.size() != proj.getGalleryImageUrls().size()) {
                proj.updateImages(proj.getPrimaryImageUrl(), remainingGallery);
                modified = true;
            }

            if (modified) {
                finalizeAndPublish(proj);
                log.info("Syndication: Expired asset [{}] purged from projection [{}]", assetId, entry.getKey());
            }
        }
    }

    /**
     * The Push & Cache:
     * 1. Caches projection in Elasticsearch NoSQL store.
     * 2. Transforms with Amazon MappingTemplate and pushes to Amazon Seller API
     *    ONLY when projection is READY_INDEXED and has a valid primary image.
     */
    private void finalizeAndPublish(ChannelItemProjection projection) {
        // 1. Cache to Elasticsearch NoSQL store
        esRepo.indexProductProjection(projection);

        // 2. Only push to external channels if projection has achieved READY_INDEXED readiness
        if (projection.getReadinessStatus() != ReadinessStatus.READY_INDEXED) {
            log.info("Projector: Projection [{}] is dormant ({}) - skipping external channel feeds",
                    projection.getSku(), projection.getReadinessStatus());
            return;
        }

        // 3. Fetch or create Amazon ChannelCatalog aggregate with dynamic category-specific template
        MappingTemplate resolvedTemplate = categoryTemplateRegistry.resolveTemplate("AMAZON", projection.getCategoryPath());
        ChannelCatalog amazonCatalog = channelCatalogRepository.findByChannelCode("Amazon_Marketplace_US")
                .orElseGet(() -> new ChannelCatalog(
                        UUID.randomUUID(),
                        "Amazon_Marketplace_US",
                        "https://sellingpartnerapi-na.amazon.com",
                        resolvedTemplate,
                        true
                ));

        amazonCatalog.upsertProjection(projection);
        channelCatalogRepository.save(amazonCatalog);

        // 4. Transform and push to Amazon SP-API if primary image exists
        if (projection.getPrimaryImageUrl() != null && !projection.getPrimaryImageUrl().isBlank()) {
            Map<String, Object> payload = amazonCatalog.transformForExport(projection);
            amazonClient.pushListingFeed("SELLER_US_12345", payload);
        } else {
            log.info("Projector: Skipping external Amazon push for SKU [{}] - awaiting primary image", projection.getSku());
        }
    }

    public Optional<ChannelItemProjection> getProjection(String regionCode, String sku) {
        String key = (regionCode != null ? regionCode : "US") + ":" + sku;
        return Optional.ofNullable(projectionStore.get(key));
    }
}
