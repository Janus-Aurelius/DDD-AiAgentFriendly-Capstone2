package com.retail.pim.bootstrap.schedulers;

import com.retail.pim.syndication.domain.events.EmbargoLiftedEvent;
import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import com.retail.pim.syndication.infrastructure.database.ElasticsearchProjectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled Poller emitting EmbargoLiftedEvent at timestamp T (CUJ 5).
 * Monitors READY_INDEXED products with active embargo timestamps. When the current
 * timestamp crosses the embargo threshold (now >= embargoUntil), it emits an EmbargoLiftedEvent
 * to invalidate edge caches and alias filters without triggering bulk document re-indexing.
 */
@Component
public class TemporalEmbargoScheduler {

    private static final Logger log = LoggerFactory.getLogger(TemporalEmbargoScheduler.class);

    private final ElasticsearchProjectionRepository esRepo;
    private final ApplicationEventPublisher eventPublisher;

    // In-memory set of lifted SKUs to guarantee exactly-once event emission per embargo window
    private final Set<String> liftedEmbargoCache = ConcurrentHashMap.newKeySet();

    public TemporalEmbargoScheduler(ElasticsearchProjectionRepository esRepo,
                                    ApplicationEventPublisher eventPublisher) {
        this.esRepo = esRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Periodic scheduled check running every minute.
     */
    @Scheduled(fixedRate = 60000)
    public void scheduleEmbargoCheck() {
        checkAndLiftEmbargos(Instant.now());
    }

    /**
     * Deterministic evaluation of all embargoed projections against a given timestamp.
     * Returns the list of newly emitted EmbargoLiftedEvents.
     */
    public List<EmbargoLiftedEvent> checkAndLiftEmbargos(Instant asOf) {
        List<EmbargoLiftedEvent> emittedEvents = new ArrayList<>();
        List<ChannelItemProjection> allProjections = esRepo.getAllProjections();

        for (ChannelItemProjection projection : allProjections) {
            if (projection.getReadinessStatus() != ReadinessStatus.READY_INDEXED) {
                continue;
            }

            Instant embargoUntil = projection.getEmbargoUntil();
            if (embargoUntil == null) {
                continue;
            }

            // Check if embargo threshold reached: asOf >= embargoUntil
            if (!asOf.isBefore(embargoUntil)) {
                String cacheKey = projection.getRegionCode() + ":" + projection.getSku() + ":" + embargoUntil.toEpochMilli();
                if (liftedEmbargoCache.add(cacheKey)) {
                    EmbargoLiftedEvent event = new EmbargoLiftedEvent(
                            projection.getSku(),
                            projection.getRegionCode(),
                            embargoUntil,
                            asOf
                    );
                    log.info("TemporalEmbargoScheduler: Lifting embargo for SKU [{}] in region [{}] (embargoUntil={})",
                            projection.getSku(), projection.getRegionCode(), embargoUntil);

                    eventPublisher.publishEvent(event);
                    emittedEvents.add(event);
                }
            }
        }

        return emittedEvents;
    }

    public boolean isEmbargoLifted(String regionCode, String sku, Instant embargoUntil) {
        if (embargoUntil == null) {
            return false;
        }
        String cacheKey = (regionCode != null ? regionCode : "US") + ":" + sku + ":" + embargoUntil.toEpochMilli();
        return liftedEmbargoCache.contains(cacheKey);
    }

    public void clearCache() {
        liftedEmbargoCache.clear();
    }
}
