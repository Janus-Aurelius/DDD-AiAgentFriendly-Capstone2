package com.retail.pim.taxonomy.infrastructure.persistence;

import com.retail.pim.taxonomy.domain.model.compliance.ComplianceRule;
import com.retail.pim.taxonomy.domain.model.compliance.RuleScope;
import com.retail.pim.taxonomy.domain.model.compliance.RuleSeverity;
import com.retail.pim.taxonomy.domain.repository.ComplianceRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-Memory persistence adapter for ComplianceRuleRepository.
 * Pre-seeds default compliance rules with explicit severity (BLOCKING vs WARNING).
 */
@Repository
public class InMemoryComplianceRuleRepository implements ComplianceRuleRepository {

    private final Map<String, ComplianceRule> rules = new ConcurrentHashMap<>();

    public InMemoryComplianceRuleRepository() {
        seedDefaultRules();
    }

    private void seedDefaultRules() {
        // Blueprint Reference Rule: European electronics voltage rating (BLOCKING)
        save(new ComplianceRule(
                "RULE_ELEC_VOLTAGE_EU",
                "European Electronics Voltage Rating",
                "European electronics must specify a 230V voltage rating",
                10,
                RuleSeverity.BLOCKING,
                RuleScope.of(List.of("electronics", "appliances"), List.of("EU", "UK", "FR"), List.of("ALL")),
                "attributes['voltage'] != null && attributes['voltage'].value == '230V'",
                "European electronics must specify a 230V voltage rating.",
                true
        ));

        // Energy Rating Compliance (BLOCKING)
        save(new ComplianceRule(
                "RULE_ELEC_ENERGY_RATING",
                "Certified Energy Rating",
                "Electronics products must specify a certified energy rating",
                20,
                RuleSeverity.BLOCKING,
                RuleScope.of(List.of("electronics", "appliances"), List.of("EU", "US"), List.of("ALL")),
                "attributes['energy_rating'] != null && !attributes['energy_rating'].asString().isBlank()",
                "Electronics products require a certified 'energy_rating' attribute before syndication.",
                true
        ));

        // EU CE Safety Certification (BLOCKING)
        save(new ComplianceRule(
                "RULE_ELEC_CE_CERT",
                "CE Conformity Marking",
                "European electronics must be CE certified",
                30,
                RuleSeverity.BLOCKING,
                RuleScope.of(List.of("electronics"), List.of("EU", "FR", "DE"), List.of("ALL")),
                "attributes['ce_certified'] != null && attributes['ce_certified'].asBoolean() == true",
                "CE safety certification is required for European electronics.",
                true
        ));

        // US FCC Certification (BLOCKING)
        save(new ComplianceRule(
                "RULE_FCC_CERT_US",
                "US FCC Compliance",
                "US electronics require an FCC ID",
                25,
                RuleSeverity.BLOCKING,
                RuleScope.of(List.of("electronics"), List.of("US"), List.of("ALL")),
                "attributes['fcc_id'] != null && !attributes['fcc_id'].asString().isBlank()",
                "US electronics require an FCC ID attribute.",
                true
        ));

        // Merchandising Content Quality Recommendation (WARNING)
        save(new ComplianceRule(
                "RULE_DESC_QUALITY",
                "Merchandising Quality Standard",
                "Detailed marketing description recommended for marketplace conversion",
                50,
                RuleSeverity.WARNING,
                RuleScope.of(List.of("ALL"), List.of("ALL"), List.of("ALL")),
                "attributes['description_quality'] != null && attributes['description_quality'].asString() == 'HIGH'",
                "Product description quality is recommended to be HIGH for optimal SEO conversion.",
                true
        ));
    }

    @Override
    public ComplianceRule save(ComplianceRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("ComplianceRule cannot be null");
        }
        rules.put(rule.getRuleId(), rule);
        return rule;
    }

    @Override
    public Optional<ComplianceRule> findById(String ruleId) {
        if (ruleId == null) return Optional.empty();
        return Optional.ofNullable(rules.get(ruleId.trim().toUpperCase()));
    }

    @Override
    public List<ComplianceRule> findAll() {
        return rules.values().stream()
                .sorted(Comparator.comparingInt(ComplianceRule::getPriority))
                .collect(Collectors.toList());
    }

    @Override
    public List<ComplianceRule> findApplicableRules(List<String> categoryHierarchy, List<String> locales, String channel) {
        return rules.values().stream()
                .filter(ComplianceRule::isActive)
                .filter(r -> r.matchesHierarchy(categoryHierarchy, locales, channel))
                .sorted(Comparator.comparingInt(ComplianceRule::getPriority))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String ruleId) {
        if (ruleId != null) {
            rules.remove(ruleId.trim().toUpperCase());
        }
    }

    @Override
    public boolean existsById(String ruleId) {
        return ruleId != null && rules.containsKey(ruleId.trim().toUpperCase());
    }
}
