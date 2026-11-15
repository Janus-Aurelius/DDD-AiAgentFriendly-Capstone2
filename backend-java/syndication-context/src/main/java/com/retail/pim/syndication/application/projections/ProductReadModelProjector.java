package com.retail.pim.syndication.application.projections;

import com.retail.pim.syndication.domain.model.ChannelCatalog;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.repository.ChannelCatalogRepository;
import com.retail.pim.syndication.domain.valueobjects.MappingTemplate;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import com.retail.pim.syndication.infrastructure.external.AmazonMarketplaceApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CQRS Read Model Projector: "The Great Aggregator".
 *
 * Flattens asynchronous event streams across:
 * - RegionalItemPublished (from Localization)
 * - ComplianceCleared (from Legal)
 * - AssetLinked (from DAM)
 *
 * Denormalizes data into high-speed ChannelItemProjections, updates Elasticsearch,
 * and pushes transformed payloads to external channels (Amazon SP-API).
 */
@Service
public class ProductReadModelProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductReadModelProjector.class);

    private final ElasticsearchProjectionRepository esRepo;
    private final AmazonMarketplaceApiClient amazonClient;
    private final ChannelCatalogRepository channelCatalogRepository;

    // Cache of active projections being aggregated: key = "region:sku"
    private final Map<String, ChannelItemProjection> projectionStore = new ConcurrentHashMap<>();

    public ProductReadModelProjector(ElasticsearchProjectionRepository esRepo,
                                     AmazonMarketplaceApiClient amazonClient,
                                     ChannelCatalogRepository channelCatalogRepository) {
        this.esRepo = esRepo;
        this.amazonClient = amazonClient;
        this.channelCatalogRepository = channelCatalogRepository;
    }

    /**
     * Aggregation Step 1: Regional item published from Localization Context
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
     */
    public void projectAssetLinked(String sku, String regionCode, String imageUrl, boolean isPrimary) {
        String key = regionCode + ":" + sku;
        ChannelItemProjection projection = projectionStore.get(key);
        if (projection != null) {
            if (isPrimary) {
                projection.updateImages(imageUrl, projection.getGalleryImageUrls());
            } else {
                List<String> updatedGallery = new java.util.ArrayList<>(projection.getGalleryImageUrls());
                updatedGallery.add(imageUrl);
                projection.updateImages(projection.getPrimaryImageUrl(), updatedGallery);
            }
            log.info("Projector: DAM Asset [{}] linked to SKU [{}] (primary={})", imageUrl, sku, isPrimary);
            finalizeAndPublish(projection);
        }
    }

    /**
     * The Push & Cache:
     * 1. Caches projection in Elasticsearch for <10ms mobile app reads.
     * 2. Transforms with Amazon MappingTemplate and pushes to Amazon Seller API.
     */
    private void finalizeAndPublish(ChannelItemProjection projection) {
        // 1. Cache to Elasticsearch NoSQL store
        esRepo.indexProductProjection(projection);

        // 2. Fetch or create Amazon ChannelCatalog aggregate
        ChannelCatalog amazonCatalog = channelCatalogRepository.findByChannelCode("Amazon_Marketplace_US")
                .orElseGet(() -> new ChannelCatalog(
                        UUID.randomUUID(),
                        "Amazon_Marketplace_US",
                        "https://sellingpartnerapi-na.amazon.com",
                        MappingTemplate.forAmazon(),
                        true
                ));

        amazonCatalog.upsertProjection(projection);
        channelCatalogRepository.save(amazonCatalog);

        // 3. Transform and push to Amazon SP-API if primary image exists
        if (projection.getPrimaryImageUrl() != null && !projection.getPrimaryImageUrl().isBlank()) {
            Map<String, Object> payload = amazonCatalog.transformForExport(projection);
            amazonClient.pushListingFeed("SELLER_US_12345", payload);
        } else {
            log.info("Projector: Skipping external Amazon push for SKU [{}] - awaiting primary image", projection.getSku());
        }
    }
}
