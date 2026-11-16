package com.retail.pim.compliance.domain.service;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.compliance.domain.events.ComplianceEvaluatedEvent;
import com.retail.pim.compliance.domain.model.ComplianceReport;
import com.retail.pim.compliance.domain.model.ComplianceViolation;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import com.retail.pim.taxonomy.domain.model.CategoryNode;
import com.retail.pim.taxonomy.domain.model.compliance.ComplianceRule;
import com.retail.pim.taxonomy.domain.repository.CategoryRepository;
import com.retail.pim.taxonomy.domain.repository.ComplianceRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ComplianceEngine Domain Service (The Orchestrator):
 * Orchestrates additive rule stacking across category hierarchies (closure table ancestors),
 * sandboxed rule evaluation, severity-aware completeness scoring, and report generation.
 */
@Service
public class ComplianceEngine {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEngine.class);

    private static final List<String> DEFAULT_TARGET_LOCALES = List.of("US", "EU");
    private static final String DEFAULT_CHANNEL = "ALL";

    private final ComplianceRuleRepository ruleRepository;
    private final RuleEvaluator ruleEvaluator;
    private final ComplianceReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CategoryRepository categoryRepository;

    public ComplianceEngine(ComplianceRuleRepository ruleRepository,
                            RuleEvaluator ruleEvaluator,
                            ComplianceReportRepository reportRepository,
                            @Autowired(required = false) ApplicationEventPublisher eventPublisher,
                            @Autowired(required = false) CategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.ruleEvaluator = ruleEvaluator;
        this.reportRepository = reportRepository;
        this.eventPublisher = eventPublisher;
        this.categoryRepository = categoryRepository;
    }

    public List<ComplianceReport> evaluateProduct(UUID productId, Long productVersion, UUID categoryId,
                                                  Map<String, AttributeValue> attributes) {
        return evaluateProduct(productId, productVersion, categoryId, null, attributes, DEFAULT_TARGET_LOCALES, DEFAULT_CHANNEL);
    }

    public List<ComplianceReport> evaluateProduct(UUID productId, UUID categoryId,
                                                  Map<String, AttributeValue> attributes) {
        return evaluateProduct(productId, 0L, categoryId, null, attributes, DEFAULT_TARGET_LOCALES, DEFAULT_CHANNEL);
    }

    /**
     * Full compliance evaluation across explicitly specified scopes and category ancestor hierarchy.
     */
    public List<ComplianceReport> evaluateProduct(UUID productId,
                                                  Long productVersion,
                                                  UUID categoryId,
                                                  String explicitCategoryCode,
                                                  Map<String, AttributeValue> attributes,
                                                  List<String> targetLocales,
                                                  String targetChannel) {
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }

        Map<String, AttributeValue> safeAttrs = attributes != null ? attributes : Collections.emptyMap();
        List<String> categoryHierarchy = resolveCategoryHierarchy(categoryId, explicitCategoryCode, safeAttrs);
        List<String> locales = (targetLocales != null && !targetLocales.isEmpty()) ? targetLocales : DEFAULT_TARGET_LOCALES;
        String channel = (targetChannel != null && !targetChannel.isBlank()) ? targetChannel.trim().toUpperCase() : DEFAULT_CHANNEL;

        log.info("ComplianceEngine: Evaluating product [{}] (v{}) for categories {} across locales {} and channel [{}]",
                productId, productVersion, categoryHierarchy, locales, channel);

        List<ComplianceReport> generatedReports = new ArrayList<>();

        for (String locale : locales) {
            String normLocale = locale.trim().toUpperCase();

            // 2. Fetch applicable rules stacking across the entire category hierarchy
            List<ComplianceRule> applicableRules = ruleRepository.findApplicableRules(categoryHierarchy, List.of(normLocale), channel);

            // 3. Sort rules by priority ascending
            applicableRules = applicableRules.stream()
                    .sorted(Comparator.comparingInt(ComplianceRule::getPriority))
                    .toList();

            log.debug("Found {} applicable rules for product [{}] in locale [{}]",
                    applicableRules.size(), productId, normLocale);

            // 4. Iterate rules and evaluate expression against product attributes
            List<ComplianceViolation> violations = new ArrayList<>();
            for (ComplianceRule rule : applicableRules) {
                boolean passed = ruleEvaluator.evaluate(rule.getExpression(), safeAttrs);
                if (!passed) {
                    violations.add(new ComplianceViolation(
                            rule.getRuleId(),
                            rule.getViolationMessage(),
                            rule.getSeverity()
                    ));
                    log.debug("Rule [{}] ({}) failed for product [{}]: {}",
                            rule.getRuleId(), rule.getSeverity(), productId, rule.getViolationMessage());
                }
            }

            // 5. Generate ComplianceReport with severity-weighted completeness score
            ComplianceReport report = ComplianceReport.create(
                    productId,
                    productVersion,
                    null,
                    normLocale,
                    channel,
                    applicableRules.size(),
                    violations
            );

            // 6. Persist the report
            reportRepository.save(report);
            generatedReports.add(report);

            log.info("ComplianceReport generated for product [{}] (locale: {}): score = {}%, status = {}, isPublishable = {}, blocking = {}",
                    productId, normLocale, report.getCompletenessScore(), report.getStatus(), report.isPublishable(), report.getBlockingViolationsCount());

            // 7. Publish domain event
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new ComplianceEvaluatedEvent(
                        report.getId(),
                        productId,
                        productVersion,
                        normLocale,
                        channel,
                        report.getCompletenessScore(),
                        report.getStatus(),
                        report.isPublishable(),
                        report.getBlockingViolationsCount(),
                        report.getWarningViolationsCount(),
                        violations
                ));
            }
        }

        return generatedReports;
    }

    private List<String> resolveCategoryHierarchy(UUID categoryId, String explicitCategoryCode, Map<String, AttributeValue> attributes) {
        List<String> hierarchy = new ArrayList<>();

        if (categoryId != null && categoryRepository != null) {
            try {
                List<CategoryNode> ancestors = categoryRepository.findAncestors(categoryId);
                if (ancestors != null && !ancestors.isEmpty()) {
                    for (CategoryNode node : ancestors) {
                        hierarchy.add(node.getCode().toLowerCase());
                    }
                } else {
                    categoryRepository.findById(categoryId).ifPresent(n -> hierarchy.add(n.getCode().toLowerCase()));
                }
            } catch (Exception e) {
                log.debug("Failed to resolve closure table ancestors for category ID [{}]: {}", categoryId, e.getMessage());
            }
        }

        if (explicitCategoryCode != null && !explicitCategoryCode.isBlank()) {
            String norm = explicitCategoryCode.trim().toLowerCase();
            if (!hierarchy.contains(norm)) {
                hierarchy.add(norm);
            }
        }

        // Also check dynamic product attributes for declared category / family_type metadata
        if (attributes != null) {
            List<String> attrKeys = List.of("category", "category_code", "family_type");
            for (String key : attrKeys) {
                AttributeValue val = attributes.get(key);
                if (val != null && !val.asString().isBlank()) {
                    String norm = val.asString().trim().toLowerCase();
                    if (!hierarchy.contains(norm)) {
                        hierarchy.add(norm);
                    }
                }
            }
        }

        if (hierarchy.isEmpty()) {
            hierarchy.add(categoryId != null ? categoryId.toString() : "general");
        }

        return Collections.unmodifiableList(hierarchy);
    }
}
