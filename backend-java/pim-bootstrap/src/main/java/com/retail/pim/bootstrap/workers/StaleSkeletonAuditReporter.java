package com.retail.pim.bootstrap.workers;

import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-destructive weekly audit reporter identifying unindexed skeleton documents older than 30 days (CUJ 5).
 * Generates operational audit reports for human merchandising review without destructive deletions.
 */
@Component
public class StaleSkeletonAuditReporter {

    private static final Logger log = LoggerFactory.getLogger(StaleSkeletonAuditReporter.class);

    private final ElasticsearchProjectionRepository esRepo;

    public record StaleSkeletonItem(
            String sku,
            String regionCode,
            Instant lastUpdated,
            long ageInDays,
            boolean pimActive,
            boolean priceInitialized,
            boolean stockInitialized
    ) {}

    public record StaleSkeletonAuditReport(
            Instant generatedAt,
            int totalSkeletonsCount,
            int staleSkeletonsCount,
            List<StaleSkeletonItem> staleItems
    ) {}

    public StaleSkeletonAuditReporter(ElasticsearchProjectionRepository esRepo) {
        this.esRepo = esRepo;
    }

    /**
     * Weekly scheduled run: Sundays at 02:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void scheduleWeeklyAudit() {
        generateAuditReport(Instant.now());
    }

    /**
     * Deterministic generation of the operational audit report against a given timestamp.
     */
    public StaleSkeletonAuditReport generateAuditReport(Instant asOf) {
        Instant staleCutoff = asOf.minus(30, ChronoUnit.DAYS);
        List<ChannelItemProjection> allProjections = esRepo.getAllProjections();

        int totalSkeletons = 0;
        List<StaleSkeletonItem> staleItems = new ArrayList<>();

        for (ChannelItemProjection projection : allProjections) {
            if (projection.getReadinessStatus() == ReadinessStatus.UNINDEXED_SKELETON) {
                totalSkeletons++;
                if (projection.getLastUpdated().isBefore(staleCutoff)) {
                    long ageInDays = ChronoUnit.DAYS.between(projection.getLastUpdated(), asOf);
                    staleItems.add(new StaleSkeletonItem(
                            projection.getSku(),
                            projection.getRegionCode(),
                            projection.getLastUpdated(),
                            ageInDays,
                            projection.isPimActive(),
                            projection.isPriceInitialized(),
                            projection.isStockInitialized()
                    ));
                }
            }
        }

        if (!staleItems.isEmpty()) {
            log.warn("Operational Audit Warning: Found {} stale unindexed skeletons older than 30 days (total skeletons={}) awaiting merchandising enrichment",
                    staleItems.size(), totalSkeletons);
        } else {
            log.info("Operational Audit: No stale unindexed skeletons found older than 30 days (total skeletons={})",
                    totalSkeletons);
        }

        return new StaleSkeletonAuditReport(asOf, totalSkeletons, staleItems.size(), staleItems);
    }
}
