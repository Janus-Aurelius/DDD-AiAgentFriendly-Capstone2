package com.retail.pim.bootstrap.workers;

import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 5: Stale Skeleton Audit Reporter Unit Tests (CUJ 5)")
class StaleSkeletonAuditReporterTest {

    private ElasticsearchProjectionRepository esRepo;
    private StaleSkeletonAuditReporter auditReporter;

    @BeforeEach
    void setUp() {
        esRepo = new ElasticsearchProjectionRepository();
        auditReporter = new StaleSkeletonAuditReporter(esRepo);
    }

    @Test
    @DisplayName("Non-destructive Audit: Reports skeletons older than 30 days without deleting them")
    void reportsStaleSkeletonsNonDestructively() {
        Instant now = Instant.parse("2026-11-24T12:00:00Z");

        // 1. Stale skeleton (age = 45 days)
        ChannelItemProjection staleSkeleton = ChannelItemProjection.createSkeleton("SKU-STALE-01", "US");
        staleSkeleton.applyOmsStock(10, 1L); // only stock initialized -> remains UNINDEXED_SKELETON
        esRepo.indexProductProjection(staleSkeleton);

        // 2. Fresh skeleton (age = 5 days)
        ChannelItemProjection freshSkeleton = ChannelItemProjection.createSkeleton("SKU-FRESH-01", "US");
        freshSkeleton.applyErpPrice(new BigDecimal("50.00"), "USD", 1L);
        esRepo.indexProductProjection(freshSkeleton);

        // 3. Fully indexed product
        ChannelItemProjection readyProduct = new ChannelItemProjection(
                UUID.randomUUID(), UUID.randomUUID(), "SKU-READY-01", "Ready Product",
                new BigDecimal("99.00"), "USD", "US"
        );
        esRepo.indexProductProjection(readyProduct);

        // Simulate age by testing against future audit time
        Instant auditTime45DaysLater = staleSkeleton.getLastUpdated().plus(45, ChronoUnit.DAYS);

        StaleSkeletonAuditReporter.StaleSkeletonAuditReport report =
                auditReporter.generateAuditReport(auditTime45DaysLater);

        assertThat(report.totalSkeletonsCount()).isEqualTo(2);
        assertThat(report.staleSkeletonsCount()).isEqualTo(2); // both are older than 30 days from 45 days later
        assertThat(report.staleItems()).extracting(StaleSkeletonAuditReporter.StaleSkeletonItem::sku)
                .containsExactlyInAnyOrder("SKU-STALE-01", "SKU-FRESH-01");

        // Non-destructive verification: Projections STILL exist in Elasticsearch repository
        assertThat(esRepo.searchBySku("US", "SKU-STALE-01")).isPresent();
        assertThat(esRepo.searchBySku("US", "SKU-FRESH-01")).isPresent();
        assertThat(esRepo.searchBySku("US", "SKU-READY-01")).isPresent();
    }

    @Test
    @DisplayName("Fresh skeletons younger than 30 days are counted in total but excluded from stale alert")
    void ignoresFreshSkeletonsUnder30Days() {
        Instant asOf = Instant.now();

        // Fresh skeleton created just now
        ChannelItemProjection freshSkeleton = ChannelItemProjection.createSkeleton("SKU-JUST-CREATED", "US");
        esRepo.indexProductProjection(freshSkeleton);

        StaleSkeletonAuditReporter.StaleSkeletonAuditReport report = auditReporter.generateAuditReport(asOf);

        assertThat(report.totalSkeletonsCount()).isEqualTo(1);
        assertThat(report.staleSkeletonsCount()).isEqualTo(0);
        assertThat(report.staleItems()).isEmpty();
    }
}
