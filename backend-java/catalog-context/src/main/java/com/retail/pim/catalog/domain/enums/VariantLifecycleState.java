package com.retail.pim.catalog.domain.enums;

/**
 * 8-State Guarded Finite State Machine (FSM) governing variant lifecycle (CUJ 6).
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

    /**
     * Validates transitions between variant lifecycle states.
     */
    public boolean canTransitionTo(VariantLifecycleState next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case SKU_DRAFT -> next == SKU_IN_REVIEW;
            case SKU_IN_REVIEW -> next == SKU_ACTIVE || next == SKU_DRAFT;
            case SKU_ACTIVE -> next == SKU_RESTRICTED || next == SKU_SUSPENDED ||
                               next == SKU_RECALLED_LOT_HOLD || next == SKU_DISCONTINUED;
            case SKU_RESTRICTED -> next == SKU_IN_REVIEW || next == SKU_ACTIVE ||
                                   next == SKU_SUSPENDED || next == SKU_DISCONTINUED;
            case SKU_SUSPENDED -> next == SKU_ACTIVE || next == SKU_DISCONTINUED;
            case SKU_RECALLED_LOT_HOLD -> next == SKU_ACTIVE || next == SKU_IN_REVIEW || next == SKU_DISCONTINUED;
            case SKU_DISCONTINUED -> next == SKU_ARCHIVED;
            case SKU_ARCHIVED -> false;
        };
    }
}
