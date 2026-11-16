package com.retail.pim.taxonomy.domain.model.compliance;

/**
 * Enumerates the compliance rule enforcement severity levels.
 */
public enum RuleSeverity {
    /**
     * Hard gate: Regulatory or critical requirement (e.g. CE safety certification, Hazmat UN code).
     * Blocking violations strictly prevent publication and external channel syndication.
     */
    BLOCKING,

    /**
     * Soft gate: Merchandising quality or completeness recommendation (e.g. Description length, image count).
     * Reduces completeness score, but does NOT block publication.
     */
    WARNING,

    /**
     * Advisory / tip: Informational suggestion for optimization without score penalties.
     */
    INFO;

    public boolean isBlocking() {
        return this == BLOCKING;
    }

    public boolean isWarning() {
        return this == WARNING;
    }

    public boolean isInfo() {
        return this == INFO;
    }
}
