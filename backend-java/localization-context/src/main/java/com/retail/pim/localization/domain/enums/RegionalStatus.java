package com.retail.pim.localization.domain.enums;

/**
 * Lifecycle states of a product catalog item within a specific operational region.
 */
public enum RegionalStatus {
    /**
     * Missing legal or mandatory regional translations (e.g. French safety warnings for Canada).
     */
    PENDING_TRANSLATION,

    /**
     * Fully localized and compliant. Ready for regional storefront syndication.
     */
    READY_FOR_REGION
}
