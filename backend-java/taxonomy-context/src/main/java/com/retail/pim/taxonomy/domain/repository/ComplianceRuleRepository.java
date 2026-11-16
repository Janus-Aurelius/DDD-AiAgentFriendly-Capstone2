package com.retail.pim.taxonomy.domain.repository;

import com.retail.pim.taxonomy.domain.model.compliance.ComplianceRule;

import java.util.List;
import java.util.Optional;

/**
 * Output Port: Repository for deterministic ComplianceRules.
 * Enables additive stacking of governance rules across Category hierarchies, Locales, and Channels.
 */
public interface ComplianceRuleRepository {

    ComplianceRule save(ComplianceRule rule);

    Optional<ComplianceRule> findById(String ruleId);

    List<ComplianceRule> findAll();

    /**
     * Stacks rules cascading down an entire category taxonomy hierarchy (ancestors -> leaf).
     */
    List<ComplianceRule> findApplicableRules(List<String> categoryHierarchy, List<String> locales, String channel);

    /**
     * Convenience method matching a single category.
     */
    default List<ComplianceRule> findApplicableRules(String category, List<String> locales, String channel) {
        return findApplicableRules(category != null ? List.of(category) : List.of(), locales, channel);
    }

    void deleteById(String ruleId);

    boolean existsById(String ruleId);
}
