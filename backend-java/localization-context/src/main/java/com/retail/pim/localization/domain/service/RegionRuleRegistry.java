package com.retail.pim.localization.domain.service;

import com.retail.pim.localization.domain.model.RegionRule;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain Service Registry: Manages operational region definitions and validation rules dynamically.
 * Enables zero-downtime addition of new market jurisdictions without code refactoring.
 */
@Component
public class RegionRuleRegistry {

    private final Map<String, RegionRule> rules = new ConcurrentHashMap<>();

    public RegionRuleRegistry() {
        // Bootstrap standard North American operational rules
        registerRule(RegionRule.usDefault());
        registerRule(RegionRule.canadaDefault());
        registerRule(RegionRule.mexicoDefault());
    }

    public void registerRule(RegionRule rule) {
        rules.put(rule.regionCode().toUpperCase(), rule);
    }

    public Optional<RegionRule> getRule(String regionCode) {
        if (regionCode == null) return Optional.empty();
        return Optional.ofNullable(rules.get(regionCode.trim().toUpperCase()));
    }

    public Collection<RegionRule> getAllOperationalRules() {
        return Collections.unmodifiableCollection(rules.values());
    }

    public Collection<String> getOperationalRegionCodes() {
        return Collections.unmodifiableSet(rules.keySet());
    }
}
