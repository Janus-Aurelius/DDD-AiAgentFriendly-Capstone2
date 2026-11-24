package com.retail.pim.syndication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.syndication.application.projections.ProductReadModelProjector;
import com.retail.pim.syndication.application.services.StorefrontReadinessGate;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import com.retail.pim.syndication.domain.repository.ChannelCatalogRepository;
import com.retail.pim.syndication.domain.service.CategoryMappingTemplateRegistry;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import com.retail.pim.syndication.infrastructure.database.InMemoryChannelCatalogRepository;
import com.retail.pim.syndication.infrastructure.external.AmazonMarketplaceApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 5: CUJ 5 End-to-End Storefront Readiness Gate Integration Test")
class StorefrontReadinessGateIntegrationTest {

    private ElasticsearchProjectionRepository esRepo;
    private AmazonMarketplaceApiClient amazonClient;
    private ChannelCatalogRepository channelCatalogRepository;
    private CategoryMappingTemplateRegistry categoryTemplateRegistry;
    private StorefrontReadinessGate readinessGate;
    private ProductReadModelProjector projector;

    @BeforeEach
    void setUp() {
        esRepo = new ElasticsearchProjectionRepository();
        amazonClient = new AmazonMarketplaceApiClient(new ObjectMapper());
        channelCatalogRepository = new InMemoryChannelCatalogRepository();
        categoryTemplateRegistry = new CategoryMappingTemplateRegistry();
        readinessGate = new StorefrontReadinessGate();
        projector = new ProductReadModelProjector(esRepo, amazonClient, channelCatalogRepository, categoryTemplateRegistry, readinessGate);
    }

    @Test
    @DisplayName("CUJ 5: Warehouse stock & promotional pricing arrive before PIM master data, embargo scheduled at T")
    void cuj5SmartphoneEmbargoedLaunchWithEarlyStockAndPricing() {
        String sku = "SKU-PHN-2026";
        String region = "US";
        Instant embargoUntil = Instant.parse("2026-12-01T00:00:00Z");

        // 1. WMS checks in 5,000 replenishment units -> OmsStockReceivedEvent
        projector.projectOmsStock(sku, region, 5000, 1L);

        // 2. ERP sets promotional pricing -> ErpPriceUpdatedEvent
        projector.projectErpPrice(sku, region, new BigDecimal("799.00"), "USD", 1L);

        // Verify: Unindexed skeleton created; not discoverable in public search
        ChannelItemProjection skeleton = esRepo.searchBySku(region, sku).orElseThrow();
        assertThat(skeleton.getReadinessStatus()).isEqualTo(ReadinessStatus.UNINDEXED_SKELETON);
        assertThat(readinessGate.isReadyForIndexing(skeleton)).isFalse();
        assertThat(readinessGate.isSearchDiscoverable(skeleton, Instant.now())).isFalse();

        // 3. Merchandisers enrich and publish PIM master data with embargo date
        UUID globalProductId = UUID.randomUUID();
        projector.projectPimMasterData(sku, globalProductId, region, "NextGen 5G Pro Smartphone",
                "Electronics > Smartphones", embargoUntil, embargoUntil, true, 1L);

        // Verify: 3 lifecycles complete -> READY_INDEXED
        ChannelItemProjection readyProjection = esRepo.searchBySku(region, sku).orElseThrow();
        assertThat(readyProjection.getReadinessStatus()).isEqualTo(ReadinessStatus.READY_INDEXED);
        assertThat(readinessGate.isReadyForIndexing(readyProjection)).isTrue();

        // 4. Before embargo timestamp: Not discoverable
        Instant queryBefore = Instant.parse("2026-11-30T23:59:59Z");
        assertThat(readinessGate.isSearchDiscoverable(readyProjection, queryBefore)).isFalse();

        List<ChannelItemProjection> searchResultsBefore = readinessGate.filterDiscoverable(
                List.of(readyProjection), queryBefore
        );
        assertThat(searchResultsBefore).isEmpty();

        // 5. At timestamp T: Embargo lifts -> Discoverable
        Instant queryAtT = Instant.parse("2026-12-01T00:00:00Z");
        assertThat(readinessGate.isSearchDiscoverable(readyProjection, queryAtT)).isTrue();

        List<ChannelItemProjection> searchResultsAtT = readinessGate.filterDiscoverable(
                List.of(readyProjection), queryAtT
        );
        assertThat(searchResultsAtT).containsExactly(readyProjection);
    }
}
