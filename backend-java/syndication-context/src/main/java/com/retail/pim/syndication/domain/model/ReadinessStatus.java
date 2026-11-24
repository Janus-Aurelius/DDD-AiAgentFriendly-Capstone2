package com.retail.pim.syndication.domain.model;

/**
 * Lifecycle readiness status for Storefront Composite Projection (CUJ 5).
 * - UNINDEXED_SKELETON: Created when OMS inventory or ERP pricing arrives before PIM master data.
 *   Remains dormant and unindexed from public storefront search until full multi-lifecycle readiness.
 * - READY_INDEXED: PIM active, ERP pricing initialized, and OMS inventory initialized. Fully discoverable.
 */
public enum ReadinessStatus {
    UNINDEXED_SKELETON,
    READY_INDEXED
}
