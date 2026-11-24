package com.retail.pim.compliance.infrastructure.gate;

import com.retail.pim.catalog.application.commands.PublishProductCommand;
import com.retail.pim.catalog.application.services.ProductLifecycleService;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.compliance.domain.repository.ComplianceReportRepository;
import com.retail.pim.compliance.domain.service.ComplianceEngine;
import com.retail.pim.compliance.domain.service.RuleEvaluator;
import com.retail.pim.compliance.infrastructure.evaluator.SpElRuleEvaluator;
import com.retail.pim.compliance.infrastructure.persistence.InMemoryComplianceReportRepository;
import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.taxonomy.domain.repository.ComplianceRuleRepository;
import com.retail.pim.taxonomy.infrastructure.persistence.InMemoryComplianceRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompliancePublishGate Hard Gate & Read-Through Tests")
class CompliancePublishGateTest {

    private ComplianceRuleRepository ruleRepository;
    private RuleEvaluator ruleEvaluator;
    private ComplianceReportRepository reportRepository;
    private ComplianceEngine complianceEngine;
    private CompliancePublishGate publishGate;
    private MockProductRepository productRepository;
    private ProductLifecycleService lifecycleService;

    static class MockProductRepository implements ProductRepository {
        final Map<UUID, ProductFamily> store = new HashMap<>();

        @Override
        public ProductFamily save(ProductFamily domain) {
            store.put(domain.getId(), domain);
            return domain;
        }

        @Override
        public Optional<ProductFamily> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<ProductFamily> findByCode(String code) {
            return store.values().stream().filter(p -> p.getCode().equalsIgnoreCase(code)).findFirst();
        }

        @Override
        public Optional<ProductFamily> findBySku(String sku) {
            return store.values().stream()
                    .filter(p -> p.getVariants().stream().anyMatch(v -> v.getSku().equalsIgnoreCase(sku)))
                    .findFirst();
        }

        @Override
        public Optional<ProductFamily> findByVariantId(UUID variantId) {
            return store.values().stream()
                    .filter(p -> p.getVariants().stream().anyMatch(v -> v.getId().equals(variantId)))
                    .findFirst();
        }

        @Override
        public List<ProductFamily> findByStatus(ProductLifecycleState status, int page, int size) {
            return store.values().stream().filter(p -> p.getStatus() == status).toList();
        }

        @Override
        public List<ProductFamily> findAll(int page, int size) {
            return List.copyOf(store.values());
        }

        @Override
        public void deleteById(UUID id) { store.remove(id); }

        @Override
        public boolean existsByCode(String code) {
            return store.values().stream().anyMatch(p -> p.getCode().equalsIgnoreCase(code));
        }
    }

    @BeforeEach
    void setUp() {
        ruleRepository = new InMemoryComplianceRuleRepository();
        ruleEvaluator = new SpElRuleEvaluator();
        reportRepository = new InMemoryComplianceReportRepository();
        complianceEngine = new ComplianceEngine(ruleRepository, ruleEvaluator, reportRepository, null, null);
        publishGate = new CompliancePublishGate(reportRepository, complianceEngine);
        productRepository = new MockProductRepository();

        lifecycleService = new ProductLifecycleService(
                productRepository,
                null,         // outbox publisher optional
                null,         // mock event publisher
                publishGate,  // wire the hard gate
                null          // compliance policy optional in unit test
        );
    }

    private ProductFamily createApprovedProduct(Map<String, AttributeValue> attrs) {
        ProductFamily product = ProductFamily.createDraft("TV-4K", "Smart TV", "4K TV", UUID.randomUUID(), attrs);

        // Add primary image to variant
        DigitalAsset primaryImg = new DigitalAsset(UUID.randomUUID(), "https://img.com/tv.jpg", DigitalAsset.AssetType.IMAGE, true);
        Dimensions dims = new Dimensions(BigDecimal.valueOf(100), BigDecimal.valueOf(60), BigDecimal.valueOf(10), BigDecimal.valueOf(12), "cm/kg");
        Variant variant = new Variant(UUID.randomUUID(), "SKU-TV-4K-01", "TV 4K SKU", BigDecimal.valueOf(499), "EUR", dims, Map.of(), List.of(primaryImg), true);
        product.addVariant(variant);

        product.enrich(Map.of("en", "English TV description"), Map.of());
        product.approve();
        return productRepository.save(product);
    }

    @Test
    @DisplayName("Hard Gate: Publish must be BLOCKED when mandatory/regulatory compliance fails for target scope")
    void shouldBlockPublishWhenComplianceFails() {
        // Product has CE & energy rating, but invalid voltage (110V instead of 230V for EU)
        Map<String, AttributeValue> attrs = Map.of(
                "family_type", AttributeValue.ofString("electronics"),
                "energy_rating", AttributeValue.ofString("A++"),
                "ce_certified", AttributeValue.ofBoolean(true),
                "voltage", AttributeValue.ofString("110V") // FAILS RULE_ELEC_VOLTAGE_EU
        );

        ProductFamily product = createApprovedProduct(attrs);

        PublishProductCommand command = new PublishProductCommand(product.getId(), "EU", "ALL", "merchandiser");

        // Attempting to publish to EU must be hard-blocked by CompliancePublishGate
        DomainException ex = assertThrows(DomainException.class, () -> lifecycleService.publishProduct(command));
        assertTrue(ex.getMessage().contains("blocking compliance violation"));
        assertTrue(ex.getMessage().contains("RULE_ELEC_VOLTAGE_EU"));

        // Product state must remain APPROVED, not published
        assertEquals(ProductLifecycleState.APPROVED, productRepository.findById(product.getId()).get().getStatus());
    }

    @Test
    @DisplayName("Hard Gate: Publish must SUCCEED when all blocking rules pass, even if WARNING rules fail")
    void shouldAllowPublishWhenOnlyWarningsExist() {
        // Product satisfies all blocking rules (230V, A++, CE), but misses description_quality (WARNING)
        Map<String, AttributeValue> attrs = Map.of(
                "family_type", AttributeValue.ofString("electronics"),
                "voltage", AttributeValue.ofString("230V"),
                "energy_rating", AttributeValue.ofString("A++"),
                "ce_certified", AttributeValue.ofBoolean(true)
        );

        ProductFamily product = createApprovedProduct(attrs);

        PublishProductCommand command = new PublishProductCommand(product.getId(), "EU", "ALL", "merchandiser");

        // Publish to EU succeeds because zero BLOCKING rules failed
        assertDoesNotThrow(() -> lifecycleService.publishProduct(command));

        ProductFamily published = productRepository.findById(product.getId()).get();
        assertEquals(ProductLifecycleState.PUBLISHED, published.getStatus());
    }

    @Test
    @DisplayName("Read-Through: Immediate synchronous evaluation executes if background report is missing or stale")
    void shouldExecuteReadThroughEvaluationWhenReportMissing() {
        Map<String, AttributeValue> attrs = Map.of(
                "family_type", AttributeValue.ofString("electronics"),
                "voltage", AttributeValue.ofString("230V"),
                "energy_rating", AttributeValue.ofString("A++"),
                "ce_certified", AttributeValue.ofBoolean(true)
        );

        ProductFamily product = createApprovedProduct(attrs);

        // Ensure reportRepository is completely empty (simulating async lag / sub-second race window)
        assertTrue(reportRepository.findByProductId(product.getId()).isEmpty());

        // canPublish triggers synchronous read-through check
        boolean publishable = publishGate.canPublish(product, "EU", "ALL");
        assertTrue(publishable);

        // Verify report was dynamically generated and stored
        assertFalse(reportRepository.findByProductId(product.getId()).isEmpty());
    }
}
