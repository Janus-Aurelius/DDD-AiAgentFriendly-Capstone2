package com.retail.pim.catalog.domain.enums;

/**
 * Granular availability state for an individual Variant SKU.
 * Allows partial product publishing without blocking the entire ProductFamily.
 */
public enum VariantAvailabilityStatus {
    /**
     * Fully compliant and available for e-commerce syndication.
     */
    AVAILABLE,

    /**
     * Missing dimensions, weights, or primary image. Excluded from channel feeds without blocking siblings.
     */
    SKU_INCOMPLETE,

    /**
     * Retired or discontinued SKU no longer sold.
     */
    DISCONTINUED
}
