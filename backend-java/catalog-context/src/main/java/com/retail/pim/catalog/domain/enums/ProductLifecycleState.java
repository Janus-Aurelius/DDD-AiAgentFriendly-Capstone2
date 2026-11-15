package com.retail.pim.catalog.domain.enums;

/**
 * Product Lifecycle States managed strictly by the ProductFamily Aggregate Root.
 */
public enum ProductLifecycleState {
    /**
     * The starting point. Accepts initial raw attributes, basic descriptions, and supplier data.
     */
    DRAFT,

    /**
     * Merchandising review complete. Localized marketing descriptions have been populated and validated.
     */
    ENRICHED,

    /**
     * Compliance & Logistics complete. Physical box dimensions, weights, and regulatory specs (e.g. energy ratings) verified.
     */
    APPROVED,

    /**
     * Final state. Visual assets (primary image per SKU) verified. Ready for e-commerce syndication and storefront publishing.
     */
    PUBLISHED;

    public boolean canTransitionTo(ProductLifecycleState next) {
        return switch (this) {
            case DRAFT -> next == ENRICHED;
            case ENRICHED -> next == APPROVED || next == DRAFT;
            case APPROVED -> next == PUBLISHED || next == ENRICHED || next == DRAFT;
            case PUBLISHED -> next == DRAFT; // Re-drafting for revisions
        };
    }
}
