package com.retail.pim.localization.domain.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Value Object defining legal, regulatory, and linguistic requirements for a commercial region.
 * Eliminates hardcoded regional matrices, allowing dynamic expansion to EU, APAC, and LATAM.
 */
public record RegionRule(
        String regionCode,
        String regionName,
        Set<String> requiredLocales,
        boolean requiresSafetyWarnings,
        boolean requiresMetricUom
) implements Serializable {

    public RegionRule {
        if (regionCode == null || regionCode.isBlank()) {
            throw new IllegalArgumentException("Region code cannot be empty");
        }
        regionCode = regionCode.trim().toUpperCase();
        requiredLocales = (requiredLocales != null) ? Collections.unmodifiableSet(requiredLocales) : Collections.emptySet();
    }

    public static RegionRule usDefault() {
        return new RegionRule("US", "United States", Set.of("en_US"), false, false);
    }

    public static RegionRule canadaDefault() {
        return new RegionRule("CA", "Canada", Set.of("fr_CA", "en_CA"), true, true);
    }

    public static RegionRule mexicoDefault() {
        return new RegionRule("MX", "Mexico", Set.of("es_MX"), false, true);
    }
}
