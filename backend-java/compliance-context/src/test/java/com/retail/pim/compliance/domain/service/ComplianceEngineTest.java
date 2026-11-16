package com.retail.pim.compliance.domain.service;

import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.compliance.domain.events.ComplianceEvaluatedEvent;
import com.retail.pim.compliance.domain.model.ComplianceReport;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import com.retail.pim.compliance.infrastructure.evaluator.SpElRuleEvaluator;
import com.retail.pim.compliance.infrastructure.messaging.ProductAttributesUpdatedListener;
import com.retail.pim.compliance.infrastructure.persistence.InMemoryComplianceReportRepository;
import com.retail.pim.taxonomy.domain.model.CategoryNode;
import com.retail.pim.taxonomy.domain.model.compliance.ComplianceRule;
import com.retail.pim.taxonomy.domain.model.compliance.RuleScope;
import com.retail.pim.taxonomy.domain.model.compliance.RuleSeverity;
import com.retail.pim.taxonomy.domain.repository.CategoryRepository;
import com.retail.pim.taxonomy.domain.repository.ComplianceRuleRepository;
import com.retail.pim.taxonomy.infrastructure.persistence.InMemoryComplianceRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ComplianceEngine Domain Service Integration Tests")
class ComplianceEngineTest {

    private ComplianceRuleRepository ruleRepository;
    private RuleEvaluator ruleEvaluator;
    private ComplianceReportRepository reportRepository;
    private TestEventPublisher eventPublisher;
    private MockCategoryRepository categoryRepository;
    private ComplianceEngine complianceEngine;

    static class TestEventPublisher implements ApplicationEventPublisher {
        final List<Object> publishedEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            publishedEvents.add(event);
        }
    }

    static class MockCategoryRepository implements CategoryRepository {
        final List<CategoryNode> ancestors = new ArrayList<>();

        @Override
        public List<CategoryNode> findAncestors(UUID categoryId) {
            return ancestors;
        }

        @Override
        public CategoryNode save(CategoryNode node) { return node; }
        @Override
        public Optional<CategoryNode> findById(UUID id) { return Optional.empty(); }
        @Override
        public Optional<CategoryNode> findByCode(String code) { return Optional.empty(); }
        @Override
        public List<CategoryNode> findAllRoots() { return List.of(); }
        @Override
        public List<CategoryNode> findAll() { return List.of(); }
        @Override
        public List<CategoryNode> findDescendants(UUID categoryId) { return List.of(); }
        @Override
        public void moveCategory(UUID categoryId, UUID newParentId) {}
        @Override
        public void deleteById(UUID id) {}
        @Override
        public boolean existsByCode(String code) { return false; }
    }

    @BeforeEach
    void setUp() {
        ruleRepository = new InMemoryComplianceRuleRepository();
        ruleEvaluator = new SpElRuleEvaluator();
        reportRepository = new InMemoryComplianceReportRepository();
        eventPublisher = new TestEventPublisher();
        categoryRepository = new MockCategoryRepository();

        complianceEngine = new ComplianceEngine(
                ruleRepository,
                ruleEvaluator,
                reportRepository,
                eventPublisher,
                categoryRepository
        );
    }

    @Test
    @DisplayName("Should evaluate European electronics compliance and flag missing voltage rating (Reference Blueprint Scenario)")
    void shouldEvaluateEuElectronicsCompliance() {
        UUID productId = UUID.randomUUID();

        // Product has energy rating, CE cert, and high desc quality, but voltage is 110V instead of 230V
        Map<String, AttributeValue> attributes = Map.of(
                "energy_rating", AttributeValue.ofString("A++"),
                "ce_certified", AttributeValue.ofBoolean(true),
                "description_quality", AttributeValue.ofString("HIGH"),
                "voltage", AttributeValue.ofString("110V")
        );

        List<ComplianceReport> reports = complianceEngine.evaluateProduct(
                productId,
                1L, // version 1
                null,
                "electronics",
                attributes,
                List.of("EU"),
                "ALL"
        );

        assertEquals(1, reports.size());
        ComplianceReport euReport = reports.get(0);

        assertEquals("EU", euReport.getTargetLocale());
        assertEquals("ACTION_REQUIRED", euReport.getStatus());
        assertFalse(euReport.isPublishable()); // Voltage is BLOCKING
        assertEquals(4, euReport.getTotalRulesEvaluated()); // Voltage, Energy Rating, CE, Description Quality
        assertEquals(3, euReport.getPassedRulesCount()); // Energy Rating, CE, Desc Quality pass
        assertEquals(75.0, euReport.getCompletenessScore()); // (3 / 4) * 100 = 75.0%
        assertEquals(1, euReport.getBlockingViolationsCount());
        assertEquals(0, euReport.getWarningViolationsCount());

        // Verify violation matches reference specification
        assertEquals(1, euReport.getViolations().size());
        assertEquals("RULE_ELEC_VOLTAGE_EU", euReport.getViolations().get(0).getRuleId());
        assertEquals("European electronics must specify a 230V voltage rating.",
                euReport.getViolations().get(0).getMessage());

        // Verify report persisted in repository
        List<ComplianceReport> savedReports = reportRepository.findByProductId(productId);
        assertEquals(1, savedReports.size());

        // Verify ComplianceEvaluatedEvent was published
        assertEquals(1, eventPublisher.publishedEvents.size());
        assertTrue(eventPublisher.publishedEvents.get(0) instanceof ComplianceEvaluatedEvent);
        ComplianceEvaluatedEvent event = (ComplianceEvaluatedEvent) eventPublisher.publishedEvents.get(0);
        assertEquals(productId, event.productId());
        assertEquals(1L, event.productVersion());
        assertEquals("EU", event.targetLocale());
        assertFalse(event.isPublishable());
    }

    @Test
    @DisplayName("Should distinguish WARNING from BLOCKING: warning allows publication with reduced score")
    void shouldAllowPublicationWithWarningViolations() {
        UUID productId = UUID.randomUUID();

        // All blocking rules pass (voltage, energy, CE), but warning rule (description_quality) fails
        Map<String, AttributeValue> attributes = Map.of(
                "voltage", AttributeValue.ofString("230V"),
                "energy_rating", AttributeValue.ofString("A+++"),
                "ce_certified", AttributeValue.ofBoolean(true)
                // missing description_quality = 'HIGH' (WARNING)
        );

        List<ComplianceReport> reports = complianceEngine.evaluateProduct(
                productId,
                2L,
                null,
                "electronics",
                attributes,
                List.of("EU"),
                "ALL"
        );

        assertEquals(1, reports.size());
        ComplianceReport euReport = reports.get(0);

        // Status is WARNING, but isPublishable is true because blockingViolationsCount == 0
        assertEquals("WARNING", euReport.getStatus());
        assertTrue(euReport.isPublishable());
        assertEquals(0, euReport.getBlockingViolationsCount());
        assertEquals(1, euReport.getWarningViolationsCount());
        assertTrue(euReport.getCompletenessScore() < 100.0);
    }

    @Test
    @DisplayName("Should cascade taxonomy rules down ancestor closure table hierarchy (Electronics -> Audio -> Wireless Earbuds)")
    void shouldCascadeTaxonomyRulesAcrossAncestors() {
        UUID productId = UUID.randomUUID();
        UUID earbudsCategoryId = UUID.randomUUID();

        // Mock closure table ancestor path: Electronics (root) -> Audio -> Wireless Earbuds (leaf)
        CategoryNode root = new CategoryNode(UUID.randomUUID(), "ELECTRONICS", "Electronics", "", null, 0);
        CategoryNode mid = new CategoryNode(UUID.randomUUID(), "AUDIO", "Audio Equipment", "", root.getId(), 1);
        CategoryNode leaf = new CategoryNode(earbudsCategoryId, "WIRELESS_EARBUDS", "Wireless Earbuds", "", mid.getId(), 2);

        categoryRepository.ancestors.addAll(List.of(root, mid, leaf));

        // Register a rule specifically scoped to "audio" category
        ruleRepository.save(new ComplianceRule(
                "RULE_AUDIO_DECIBEL_LIMIT",
                "Max Volume Warning",
                "Audio devices must comply with EU 85dB hearing safety standard",
                15,
                RuleSeverity.BLOCKING,
                RuleScope.of(List.of("audio"), List.of("EU"), List.of("ALL")),
                "attributes['max_decibels'] != null && attributes['max_decibels'].value <= 85",
                "Audio devices in EU must have a certified max_decibels rating <= 85dB.",
                true
        ));

        // Evaluate earbuds product that has 230V and CE, but missing max_decibels
        Map<String, AttributeValue> attributes = Map.of(
                "voltage", AttributeValue.ofString("230V"),
                "energy_rating", AttributeValue.ofString("A"),
                "ce_certified", AttributeValue.ofBoolean(true),
                "description_quality", AttributeValue.ofString("HIGH")
        );

        List<ComplianceReport> reports = complianceEngine.evaluateProduct(
                productId,
                1L,
                earbudsCategoryId,
                null,
                attributes,
                List.of("EU"),
                "ALL"
        );

        assertEquals(1, reports.size());
        ComplianceReport report = reports.get(0);

        // Verify that the rule for parent category "audio" cascaded to leaf "wireless_earbuds"
        assertTrue(report.getViolations().stream().anyMatch(v -> "RULE_AUDIO_DECIBEL_LIMIT".equals(v.getRuleId())));
        assertFalse(report.isPublishable());
    }

    @Test
    @DisplayName("ProductAttributesUpdatedListener should automatically trigger ComplianceEngine on event with version")
    void shouldTriggerEngineViaEventListener() {
        ProductAttributesUpdatedListener listener = new ProductAttributesUpdatedListener(complianceEngine, null);

        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Map<String, AttributeValue> attrs = Map.of(
                "voltage", AttributeValue.ofString("230V"),
                "energy_rating", AttributeValue.ofString("A"),
                "ce_certified", AttributeValue.ofBoolean(true),
                "description_quality", AttributeValue.ofString("HIGH")
        );

        ProductAttributesUpdatedEvent event = new ProductAttributesUpdatedEvent(productId, 3L, categoryId, attrs);
        listener.onProductAttributesUpdated(event);

        List<ComplianceReport> reports = reportRepository.findByProductId(productId);
        assertFalse(reports.isEmpty());
        assertEquals(3L, reports.get(0).getProductVersion());
    }
}
