package com.retail.pim.syndication.application.projections;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 5: Storefront Readiness Gate & Virtual Stock Math Tests (CUJ 5)")
class StorefrontReadinessGateTest {

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
    @DisplayName("3-Lifecycle Gate: Skeletons remain UNINDEXED until PIM, ERP, and OMS are all initialized")
    void threeLifecycleReadinessGate() {
        String sku = "SKU-PHN-01";
        String region = "US";

        // Step 1: Only OMS stock arrives
        projector.projectOmsStock(sku, region, 5000, 1L);
        ChannelItemProjection projAfterStock = projector.getProjection(region, sku).orElseThrow();
        assertThat(projAfterStock.getReadinessStatus()).isEqualTo(ReadinessStatus.UNINDEXED_SKELETON);
        assertThat(readinessGate.isReadyForIndexing(projAfterStock)).isFalse();

        // Step 2: ERP price arrives
        projector.projectErpPrice(sku, region, new BigDecimal("899.99"), "USD", 1L);
        ChannelItemProjection projAfterPrice = projector.getProjection(region, sku).orElseThrow();
        assertThat(projAfterPrice.getReadinessStatus()).isEqualTo(ReadinessStatus.UNINDEXED_SKELETON);
        assertThat(readinessGate.isReadyForIndexing(projAfterPrice)).isFalse();

        // Step 3: PIM master data arrives -> all 3 lifecycles satisfied
        projector.projectPimMasterData(sku, UUID.randomUUID(), region, "Pro Flagship Phone",
                "Smartphones", null, null, true, 1L);
        ChannelItemProjection projFull = projector.getProjection(region, sku).orElseThrow();
        assertThat(projFull.getReadinessStatus()).isEqualTo(ReadinessStatus.READY_INDEXED);
        assertThat(readinessGate.isReadyForIndexing(projFull)).isTrue();
    }

    @Test
    @DisplayName("Temporal Embargo Gate: Ready product is hidden before embargoUntil and visible at/after")
    void temporalEmbargoDiscoveryGate() {
        String sku = "SKU-EMBARGO-01";
        String region = "US";
        Instant embargoUntil = Instant.parse("2026-12-01T00:00:00Z");

        projector.projectOmsStock(sku, region, 100, 1L);
        projector.projectErpPrice(sku, region, new BigDecimal("199.99"), "USD", 1L);
        projector.projectPimMasterData(sku, UUID.randomUUID(), region, "Embargoed Gadget",
                "Gadgets", embargoUntil, null, true, 1L);

        ChannelItemProjection projection = projector.getProjection(region, sku).orElseThrow();
        assertThat(projection.getReadinessStatus()).isEqualTo(ReadinessStatus.READY_INDEXED);

        // Before embargo
        Instant before = Instant.parse("2026-11-30T23:59:59Z");
        assertThat(readinessGate.isSearchDiscoverable(projection, before)).isFalse();

        // At embargo threshold
        Instant atThreshold = Instant.parse("2026-12-01T00:00:00Z");
        assertThat(readinessGate.isSearchDiscoverable(projection, atThreshold)).isTrue();

        // After embargo threshold
        Instant after = Instant.parse("2026-12-01T00:00:01Z");
        assertThat(readinessGate.isSearchDiscoverable(projection, after)).isTrue();

        // Filter collection
        List<ChannelItemProjection> discoverableBefore = readinessGate.filterDiscoverable(List.of(projection), before);
        assertThat(discoverableBefore).isEmpty();

        List<ChannelItemProjection> discoverableAfter = readinessGate.filterDiscoverable(List.of(projection), after);
        assertThat(discoverableAfter).containsExactly(projection);
    }

    @Test
    @DisplayName("Virtual Bundle ATP Math: Calculates min(floor(Stock_i / Qty_i)) across components")
    void virtualBundleAtpMathCalculation() {
        // Component A: stock 10, required 2 -> 5
        // Component B: stock 21, required 3 -> 7
        // Component C: stock 4, required 1 -> 4
        // Expected bundle ATP = min(5, 7, 4) = 4
        Map<String, Integer> componentStock = Map.of(
                "COMP-A", 10,
                "COMP-B", 21,
                "COMP-C", 4
        );
        Map<String, Integer> componentQty = Map.of(
                "COMP-A", 2,
                "COMP-B", 3,
                "COMP-C", 1
        );

        int bundleAtp = projector.calculateBundleAtp(componentStock, componentQty);
        assertThat(bundleAtp).isEqualTo(4);

        // When one component has 0 stock, bundle ATP drops to 0
        Map<String, Integer> depletedStock = Map.of(
                "COMP-A", 10,
                "COMP-B", 0,
                "COMP-C", 4
        );
        int depletedAtp = projector.calculateBundleAtp(depletedStock, componentQty);
        assertThat(depletedAtp).isEqualTo(0);
    }

    @Test
    @DisplayName("Debounced Virtual Bundle ATP: Buffers rapid writes and flushes at bounded intervals")
    void debouncedVirtualBundleAtpAccumulation() {
        String bundleSku = "BNDL-CONSOLE-01";
        String region = "US";
        Instant t0 = Instant.parse("2026-11-24T10:00:00Z");

        // Initialize bundle projection in projector
        projector.projectOmsStock(bundleSku, region, 0, 1L);

        // Accumulate 3 rapid stock events
        projector.accumulateBundleAtp(bundleSku, region, 15, t0);
        projector.accumulateBundleAtp(bundleSku, region, 12, t0.plusSeconds(1));
        projector.accumulateBundleAtp(bundleSku, region, 9, t0.plusSeconds(2));

        assertThat(projector.getAccumulatedBundleAtp(region, bundleSku)).contains(9);

        // First flush at t0 + 2s (initial flush succeeds)
        Map<String, Integer> flushedFirst = projector.flushDebouncedBundleAtp(t0.plusSeconds(2), Duration.ofSeconds(5));
        assertThat(flushedFirst).containsEntry(bundleSku, 9);

        // New rapid update arrives at t0 + 3s
        projector.accumulateBundleAtp(bundleSku, region, 7, t0.plusSeconds(3));

        // Attempting to flush at t0 + 4s (< 5s debounce window) is debounced
        Map<String, Integer> flushedTooEarly = projector.flushDebouncedBundleAtp(t0.plusSeconds(4), Duration.ofSeconds(5));
        assertThat(flushedTooEarly).isEmpty();

        // Flushing at t0 + 8s (>= 5s debounce window) succeeds
        Map<String, Integer> flushedAfterWindow = projector.flushDebouncedBundleAtp(t0.plusSeconds(8), Duration.ofSeconds(5));
        assertThat(flushedAfterWindow).containsEntry(bundleSku, 7);
    }

    @Test
    @DisplayName("Monotonic Sequence Guard: Discards stale versions across PIM, ERP, and OMS")
    void monotonicSequenceGuard() {
        String sku = "SKU-MONO-01";
        String region = "US";

        // Apply OMS v2
        projector.projectOmsStock(sku, region, 100, 2L);
        ChannelItemProjection proj = projector.getProjection(region, sku).orElseThrow();
        assertThat(proj.getAvailableStock()).isEqualTo(100);
        assertThat(proj.getOmsLastProcessedVersion()).isEqualTo(2L);

        // Delayed OMS v1 arrives -> discarded as stale
        projector.projectOmsStock(sku, region, 50, 1L);
        assertThat(proj.getAvailableStock()).isEqualTo(100);
        assertThat(proj.getOmsLastProcessedVersion()).isEqualTo(2L);

        // Apply ERP v5
        projector.projectErpPrice(sku, region, new BigDecimal("250.00"), "USD", 5L);
        assertThat(proj.getPrice()).isEqualByComparingTo("250.00");
        assertThat(proj.getErpLastProcessedVersion()).isEqualTo(5L);

        // Delayed ERP v4 arrives -> discarded as stale
        projector.projectErpPrice(sku, region, new BigDecimal("199.00"), "USD", 4L);
        assertThat(proj.getPrice()).isEqualByComparingTo("250.00");
        assertThat(proj.getErpLastProcessedVersion()).isEqualTo(5L);
    }
}
