package com.retail.pim.bootstrap.schedulers;

import com.retail.pim.syndication.domain.events.EmbargoLiftedEvent;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 5: Temporal Embargo Scheduler Unit Tests (CUJ 5)")
class TemporalEmbargoSchedulerTest {

    private ElasticsearchProjectionRepository esRepo;
    private List<EmbargoLiftedEvent> capturedEvents;
    private ApplicationEventPublisher eventPublisher;
    private TemporalEmbargoScheduler scheduler;

    @BeforeEach
    void setUp() {
        esRepo = new ElasticsearchProjectionRepository();
        capturedEvents = new ArrayList<>();
        eventPublisher = event -> {
            if (event instanceof EmbargoLiftedEvent e) {
                capturedEvents.add(e);
            }
        };
        scheduler = new TemporalEmbargoScheduler(esRepo, eventPublisher);
    }

    @Test
    @DisplayName("Emits EmbargoLiftedEvent at timestamp T and does not duplicate on repeat runs")
    void liftsEmbargoAtTimestampTWithDeduplication() {
        String sku = "SKU-FLAGSHIP-01";
        String region = "US";
        Instant embargoUntil = Instant.parse("2026-12-01T00:00:00Z");

        ChannelItemProjection projection = new ChannelItemProjection(
                UUID.randomUUID(), UUID.randomUUID(), sku, "Flagship Phone",
                new BigDecimal("999.00"), "USD", region
        );
        projection.setEmbargoUntil(embargoUntil);
        esRepo.indexProductProjection(projection);

        // Run scheduler before embargo
        Instant before = Instant.parse("2026-11-30T23:59:59Z");
        List<EmbargoLiftedEvent> eventsBefore = scheduler.checkAndLiftEmbargos(before);
        assertThat(eventsBefore).isEmpty();
        assertThat(capturedEvents).isEmpty();
        assertThat(scheduler.isEmbargoLifted(region, sku, embargoUntil)).isFalse();

        // Run scheduler at embargo timestamp T
        Instant atT = Instant.parse("2026-12-01T00:00:00Z");
        List<EmbargoLiftedEvent> eventsAtT = scheduler.checkAndLiftEmbargos(atT);
        assertThat(eventsAtT).hasSize(1);
        assertThat(eventsAtT.get(0).sku()).isEqualTo(sku);
        assertThat(eventsAtT.get(0).embargoUntil()).isEqualTo(embargoUntil);
        assertThat(capturedEvents).hasSize(1);
        assertThat(scheduler.isEmbargoLifted(region, sku, embargoUntil)).isTrue();

        // Repeat run at T + 10s -> Deduplicated, no new event emitted
        List<EmbargoLiftedEvent> eventsRepeat = scheduler.checkAndLiftEmbargos(atT.plusSeconds(10));
        assertThat(eventsRepeat).isEmpty();
        assertThat(capturedEvents).hasSize(1);
    }

    @Test
    @DisplayName("Unindexed skeletons with active embargo do not emit EmbargoLiftedEvent until READY_INDEXED")
    void ignoresUnindexedSkeletonsUntilReady() {
        String sku = "SKU-SKELETON-01";
        String region = "US";
        Instant embargoUntil = Instant.parse("2026-12-01T00:00:00Z");

        ChannelItemProjection skeleton = ChannelItemProjection.createSkeleton(sku, region);
        skeleton.setEmbargoUntil(embargoUntil);
        esRepo.indexProductProjection(skeleton);

        Instant atT = Instant.parse("2026-12-01T00:00:00Z");
        List<EmbargoLiftedEvent> events = scheduler.checkAndLiftEmbargos(atT);
        assertThat(events).isEmpty();
        assertThat(capturedEvents).isEmpty();

        // When skeleton is completed to READY_INDEXED
        skeleton.applyPimMasterData(UUID.randomUUID(), "Complete Title", "Gadgets",
                embargoUntil, null, true, 1L);
        skeleton.applyErpPrice(new BigDecimal("100.00"), "USD", 1L);
        skeleton.applyOmsStock(50, 1L);
        esRepo.indexProductProjection(skeleton);

        List<EmbargoLiftedEvent> eventsAfterReady = scheduler.checkAndLiftEmbargos(atT);
        assertThat(eventsAfterReady).hasSize(1);
        assertThat(eventsAfterReady.get(0).sku()).isEqualTo(sku);
    }
}
