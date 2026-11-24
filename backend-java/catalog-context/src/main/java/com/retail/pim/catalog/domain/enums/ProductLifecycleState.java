package com.retail.pim.catalog.domain.enums;

/**
 * Product Lifecycle States managed by ProductFamily.
 * @deprecated Legacy linear lifecycle state. Modern FSM architecture delegates to
 * {@link FamilyLifecycleState} for aggregate family status and {@link VariantLifecycleState} for concrete SKUs.
 */
@Deprecated
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
