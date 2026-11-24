package com.retail.pim.syndication.infrastructure.database;

import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter: Simulates Elasticsearch NoSQL Document Store for sub-10ms e-commerce read operations.
 * Stores denormalized JSON projections indexed by SKU and Region.
 */
@Repository
public class ElasticsearchProjectionRepository {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProjectionRepository.class);

    // Simulated Elasticsearch index: key = "region:sku"
    private final Map<String, ChannelItemProjection> searchIndex = new ConcurrentHashMap<>();

    public void indexProductProjection(ChannelItemProjection projection) {
        String key = projection.getRegionCode() + ":" + projection.getSku();
        searchIndex.put(key, projection);
        log.info("Elasticsearch: Indexed denormalized product document [{}] for region [{}] with readiness [{}]",
                projection.getSku(), projection.getRegionCode(), projection.getReadinessStatus());
    }

    public Optional<ChannelItemProjection> searchBySku(String regionCode, String sku) {
        String key = regionCode + ":" + sku;
        return Optional.ofNullable(searchIndex.get(key));
    }

    /**
     * Fast-path Mobile / Search query: Returns masked SummaryProjection (<1KB)
     * preventing bandwidth and cache explosion.
     */
    public Optional<ChannelItemProjection.SummaryProjection> searchSummaryBySku(String regionCode, String sku) {
        return searchBySku(regionCode, sku).map(ChannelItemProjection::toSummaryProjection);
    }

    /**
     * Full Product Detail Page (PDP) query: Returns full DetailProjection.
     */
    public Optional<ChannelItemProjection.DetailProjection> searchDetailBySku(String regionCode, String sku) {
        return searchBySku(regionCode, sku).map(ChannelItemProjection::toDetailProjection);
    }

    public List<ChannelItemProjection.SummaryProjection> searchSummariesByRegion(String regionCode) {
        return searchIndex.values().stream()
                .filter(p -> p.getRegionCode().equalsIgnoreCase(regionCode))
                .map(ChannelItemProjection::toSummaryProjection)
                .toList();
    }

    public List<ChannelItemProjection> getAllProjections() {
        return new ArrayList<>(searchIndex.values());
    }

    public void deleteBySku(String regionCode, String sku) {
        String key = regionCode + ":" + sku;
        searchIndex.remove(key);
        log.info("Elasticsearch: Deleted projection [{}] for region [{}]", sku, regionCode);
    }
}
