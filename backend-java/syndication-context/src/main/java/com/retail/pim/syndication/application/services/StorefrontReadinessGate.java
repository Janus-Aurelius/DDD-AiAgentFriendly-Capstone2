package com.retail.pim.syndication.application.services;

import com.retail.pim.syndication.domain.model.ChannelItemProjection;
import com.retail.pim.syndication.domain.model.ReadinessStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Gatekeeper enforcing multi-lifecycle readiness and temporal embargo discovery (CUJ 5).
 * Only products that have satisfied all 3 lifecycles (PIM active, ERP price initialized, OMS stock initialized)
 * and whose embargo timestamp has passed are permitted into storefront search index results.
 */
@Service
public class StorefrontReadinessGate {

    /**
     * Verifies if projection has satisfied the 3-lifecycle readiness prerequisites.
     */
    public boolean isReadyForIndexing(ChannelItemProjection projection) {
        if (projection == null) {
            return false;
        }
        return projection.getReadinessStatus() == ReadinessStatus.READY_INDEXED;
    }

    /**
     * Evaluates dynamic storefront search predicate against given timestamp.
     */
    public boolean isSearchDiscoverable(ChannelItemProjection projection, Instant asOf) {
        if (projection == null) {
            return false;
        }
        return projection.isDiscoverable(asOf);
    }

    /**
     * Filters collection of projections down to discoverable items.
     */
    public List<ChannelItemProjection> filterDiscoverable(Collection<ChannelItemProjection> projections, Instant asOf) {
        if (projections == null) {
            return List.of();
        }
        return projections.stream()
                .filter(p -> isSearchDiscoverable(p, asOf))
                .toList();
    }
}
