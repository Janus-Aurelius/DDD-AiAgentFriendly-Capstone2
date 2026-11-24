package com.retail.pim.syndication.application.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.domain.events.ProductTombstonedEvent;
import com.retail.pim.syndication.application.projections.ProductReadModelProjector;
import com.retail.pim.syndication.application.services.StorefrontReadinessGate;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.repository.ChannelCatalogRepository;
import com.retail.pim.syndication.domain.service.CategoryMappingTemplateRegistry;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import com.retail.pim.syndication.infrastructure.database.InMemoryChannelCatalogRepository;
import com.retail.pim.syndication.infrastructure.external.AmazonMarketplaceApiClient;
import com.retail.pim.syndication.infrastructure.messaging.ProductTombstonedEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 8: CQRS Monotonic Sequence Upsert & Tombstone Purge Tests (CUJ 8)")
class CqrsMonotonicUpsertTest {

    private ElasticsearchProjectionRepository esRepo;
    private ProductReadModelProjector projector;
    private ExternalMonotonicSequenceProjector monotonicProjector;
    private ProductTombstonedEventListener tombstoneListener;

    @BeforeEach
    void setUp() {
        esRepo = new ElasticsearchProjectionRepository();
        AmazonMarketplaceApiClient amazonClient = new AmazonMarketplaceApiClient(new ObjectMapper());
        ChannelCatalogRepository channelCatalogRepository = new InMemoryChannelCatalogRepository();
        CategoryMappingTemplateRegistry categoryTemplateRegistry = new CategoryMappingTemplateRegistry();
        StorefrontReadinessGate readinessGate = new StorefrontReadinessGate();

        projector = new ProductReadModelProjector(
                esRepo, amazonClient, channelCatalogRepository, categoryTemplateRegistry, readinessGate
        );
        monotonicProjector = new ExternalMonotonicSequenceProjector(projector);
        tombstoneListener = new ProductTombstonedEventListener(projector);
    }

    @Test
    @DisplayName("Monotonic guard applies ERP pricing in order, advances version, and discards stale out-of-order events")
    void monotonicGuardEnforcesPricingOrdering() {
        String sku = "SKU-MONO-PRICE";
        String region = "US";

        // Version 1 arrives: should apply
        boolean applied1 = monotonicProjector.processPricingUpdate(sku, region, new BigDecimal("100.00"), "USD", 1L);
        assertThat(applied1).isTrue();
        assertThat(monotonicProjector.getLastProcessedVersion(ExternalMonotonicSequenceProjector.STREAM_PRICING, region, sku))
                .isEqualTo(1L);

        ChannelItemProjection projection1 = projector.getProjection(region, sku).orElseThrow();
        assertThat(projection1.getPrice()).isEqualByComparingTo("100.00");

        // Version 5 arrives: should apply and advance version
        boolean applied5 = monotonicProjector.processPricingUpdate(sku, region, new BigDecimal("120.00"), "USD", 5L);
        assertThat(applied5).isTrue();
        assertThat(monotonicProjector.getLastProcessedVersion(ExternalMonotonicSequenceProjector.STREAM_PRICING, region, sku))
                .isEqualTo(5L);

        ChannelItemProjection projection5 = projector.getProjection(region, sku).orElseThrow();
        assertThat(projection5.getPrice()).isEqualByComparingTo("120.00");

        // Stale Version 3 arrives (3 <= 5): should be discarded without mutating state
        boolean applied3 = monotonicProjector.processPricingUpdate(sku, region, new BigDecimal("80.00"), "USD", 3L);
        assertThat(applied3).isFalse();
        assertThat(monotonicProjector.getLastProcessedVersion(ExternalMonotonicSequenceProjector.STREAM_PRICING, region, sku))
                .isEqualTo(5L);

        ChannelItemProjection projectionUnchanged = projector.getProjection(region, sku).orElseThrow();
        assertThat(projectionUnchanged.getPrice()).isEqualByComparingTo("120.00");

        // Duplicate Version 5 arrives (5 <= 5): should be discarded
        boolean appliedDup5 = monotonicProjector.processPricingUpdate(sku, region, new BigDecimal("125.00"), "USD", 5L);
        assertThat(appliedDup5).isFalse();
        assertThat(projector.getProjection(region, sku).orElseThrow().getPrice())
                .isEqualByComparingTo("120.00");

        // Version 6 arrives: should apply
        boolean applied6 = monotonicProjector.processPricingUpdate(sku, region, new BigDecimal("130.00"), "USD", 6L);
        assertThat(applied6).isTrue();
        assertThat(projector.getProjection(region, sku).orElseThrow().getPrice())
                .isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Monotonic guard applies OMS inventory in order and discards out-of-order version events")
    void monotonicGuardEnforcesInventoryOrdering() {
        String sku = "SKU-MONO-STOCK";
        String region = "US";

        // Version 10 arrives with stock 500
        boolean applied10 = monotonicProjector.processInventoryUpdate(sku, region, 500, 10L);
        assertThat(applied10).isTrue();
        assertThat(projector.getProjection(region, sku).orElseThrow().getAvailableStock()).isEqualTo(500);

        // Stale Version 8 arrives with stock 10
        boolean applied8 = monotonicProjector.processInventoryUpdate(sku, region, 10, 8L);
        assertThat(applied8).isFalse();
        assertThat(projector.getProjection(region, sku).orElseThrow().getAvailableStock()).isEqualTo(500);

        // Version 15 arrives with stock 450
        boolean applied15 = monotonicProjector.processInventoryUpdate(sku, region, 450, 15L);
        assertThat(applied15).isTrue();
        assertThat(projector.getProjection(region, sku).orElseThrow().getAvailableStock()).isEqualTo(450);
    }

    @Test
    @DisplayName("ProductTombstonedEventListener purges projections from in-memory cache and Elasticsearch read-model")
    void productTombstonedEventPurgesEdgeProjections() {
        String sku = "SKU-TOMBSTONE-EDGE";
        String region = "US";

        // Initialize projection in cache and Elasticsearch
        projector.projectOmsStock(sku, region, 100, 1L);
        projector.projectErpPrice(sku, region, new BigDecimal("59.99"), "USD", 1L);

        assertThat(projector.getProjection(region, sku)).isPresent();
        assertThat(esRepo.searchBySku(region, sku)).isPresent();

        // Fire ProductTombstonedEvent through listener
        ProductTombstonedEvent event = new ProductTombstonedEvent(sku, UUID.randomUUID(), "Permanent EOL Decommissioning");
        tombstoneListener.onProductTombstoned(event);

        // Verify: Projector cache purged and Elasticsearch document deleted
        Optional<ChannelItemProjection> cachedAfterPurge = projector.getProjection(region, sku);
        assertThat(cachedAfterPurge).isEmpty();

        Optional<ChannelItemProjection> esAfterPurge = esRepo.searchBySku(region, sku);
        assertThat(esAfterPurge).isEmpty();
    }
}
