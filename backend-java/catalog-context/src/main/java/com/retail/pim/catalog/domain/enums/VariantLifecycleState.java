package com.retail.pim.catalog.domain.enums;

/**
 * 8-State Guarded Finite State Machine (FSM) governing variant lifecycle.
 */
public enum VariantLifecycleState {
    SKU_DRAFT,
    SKU_IN_REVIEW,
    SKU_ACTIVE,
    SKU_RESTRICTED,
    SKU_SUSPENDED,
    SKU_RECALLED_LOT_HOLD,
    SKU_DISCONTINUED,
    SKU_ARCHIVED;

    /**
     * Checks whether this state represents a globally active viable SKU.
     */
    public boolean isActive() {
        return this == SKU_ACTIVE || this == SKU_RESTRICTED;
    }

    /**
     * Checks whether this state permits editing via Working Revisions.
     */
    public boolean allowsRevision() {
        return this != SKU_DISCONTINUED && this != SKU_ARCHIVED;
    }
}
