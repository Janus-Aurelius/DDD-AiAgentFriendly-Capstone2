package com.retail.pim.catalog.domain.enums;

/**
 * Reasons for applying a regional or channel availability mask.
 */
public enum RestrictionReason {
    PROP_65_NON_COMPLIANT,
    REGIONAL_COMPLIANCE_HOLD,
    SAFETY_RESTRICTION,
    ENVIRONMENTAL_REGULATION,
    REGIONAL_REGULATION,
    CHANNEL_POLICY_VIOLATION,
    RESTRICTED
}
