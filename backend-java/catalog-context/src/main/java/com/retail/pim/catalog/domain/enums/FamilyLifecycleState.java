package com.retail.pim.catalog.domain.enums;

/**
 * 4-State Guarded Finite State Machine governing ProductFamily aggregate lifecycle (CUJ 6).
 * - FAMILY_DRAFT: Initial authoring state before any variant is published.
 * - FAMILY_PUBLISHED: At least one variant is SKU_ACTIVE.
 * - FAMILY_SUSPENDED: activeVariantCount == 0 OR activeSuspensionReasons is not empty.
 * - FAMILY_ARCHIVED: All variants SKU_ARCHIVED / retention expired.
 */
public enum FamilyLifecycleState {
    FAMILY_DRAFT,
    FAMILY_PUBLISHED,
    FAMILY_SUSPENDED,
    FAMILY_ARCHIVED;

    public boolean canTransitionTo(FamilyLifecycleState next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case FAMILY_DRAFT -> next == FAMILY_PUBLISHED || next == FAMILY_ARCHIVED;
            case FAMILY_PUBLISHED -> next == FAMILY_SUSPENDED || next == FAMILY_ARCHIVED;
            case FAMILY_SUSPENDED -> next == FAMILY_PUBLISHED || next == FAMILY_ARCHIVED;
            case FAMILY_ARCHIVED -> false;
        };
    }
}
