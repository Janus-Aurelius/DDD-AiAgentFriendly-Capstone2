package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.events.ProductTombstonedEvent;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Slice 8: Product Tombstoning & Decommissioning Unit Tests (CUJ 8)")
class ProductTombstoningTest {

    private InMemoryProductRepository productRepository;
    private TestEventPublisher eventPublisher;
    private VariantDecommissionService decommissionService;

    private UUID familyId;
    private ProductFamily testFamily;
    private Variant variant1;
    private Variant variant2;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        eventPublisher = new TestEventPublisher();
        decommissionService = new VariantDecommissionService(productRepository, eventPublisher);

        familyId = UUID.randomUUID();
        testFamily = new ProductFamily(
                familyId, "FAM-DECOM", "Decommission Family", "Lifecycle Test Family",
                Map.of("en", "Decommission Family"), UUID.randomUUID(),
                ProductLifecycleState.PUBLISHED, Map.of(), null, 1, 1L,
                Collections.emptySet(), FamilyLifecycleState.FAMILY_PUBLISHED
        );

        variant1 = new Variant(UUID.randomUUID(), "SKU-DECOM-1", "Variant 1", new BigDecimal("19.99"), "USD", null, null, null, true);
        variant1.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);

        variant2 = new Variant(UUID.randomUUID(), "SKU-DECOM-2", "Variant 2", new BigDecimal("29.99"), "USD", null, null, null, true);
        variant2.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);

        testFamily.addVariant(variant1);
        testFamily.addVariant(variant2);
        productRepository.save(testFamily);
    }

    @Test
    @DisplayName("Decommissioning a variant transitions to SKU_DISCONTINUED and emits ProductTombstonedEvent")
    void decommissionVariantEmitsTombstoneEvent() {
        Variant decommissioned = decommissionService.decommissionVariant("SKU-DECOM-1", "Product End-of-Life Phaseout");

        assertThat(decommissioned.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_DISCONTINUED);
        assertThat(decommissioned.isActive()).isFalse();

        // Verify ProductTombstonedEvent published
        assertThat(eventPublisher.publishedEvents).hasSize(1);
        DomainEvent published = eventPublisher.publishedEvents.get(0);
        assertThat(published).isInstanceOf(ProductTombstonedEvent.class);

        ProductTombstonedEvent tombstone = (ProductTombstonedEvent) published;
        assertThat(tombstone.sku()).isEqualTo("SKU-DECOM-1");
        assertThat(tombstone.familyId()).isEqualTo(familyId);
        assertThat(tombstone.reason()).isEqualTo("Product End-of-Life Phaseout");

        // Relational persistence check: product family still retains the record for warranty lookups
        ProductFamily reloaded = productRepository.findById(familyId).orElseThrow();
        assertThat(reloaded.getVariants()).hasSize(2);
        assertThat(reloaded.getVariantBySku("SKU-DECOM-1").getLifecycleState())
                .isEqualTo(VariantLifecycleState.SKU_DISCONTINUED);

        // Since variant 2 is still active, family remains published
        assertThat(reloaded.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
    }

    @Test
    @DisplayName("Decommissioning the last active variant auto-suspends parent family (Last Variant Standing)")
    void decommissionLastVariantSuspendsFamily() {
        // Decommission first variant
        decommissionService.decommissionVariant("SKU-DECOM-1", "Phaseout 1");
        // Decommission second variant
        decommissionService.decommissionVariant("SKU-DECOM-2", "Phaseout 2");

        ProductFamily reloaded = productRepository.findById(familyId).orElseThrow();
        assertThat(reloaded.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);
        assertThat(reloaded.getActiveSuspensionReasons()).contains("NO_ACTIVE_VARIANTS");
        assertThat(eventPublisher.publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("Archiving a discontinued variant transitions to SKU_ARCHIVED after retention period")
    void archiveVariantTransitionsState() {
        decommissionService.decommissionVariant("SKU-DECOM-1", "End-of-life");
        Variant archived = decommissionService.archiveVariant("SKU-DECOM-1");

        assertThat(archived.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_ARCHIVED);
        assertThat(archived.isActive()).isFalse();

        ProductFamily reloaded = productRepository.findById(familyId).orElseThrow();
        assertThat(reloaded.getVariantBySku("SKU-DECOM-1").getLifecycleState())
                .isEqualTo(VariantLifecycleState.SKU_ARCHIVED);
    }

    @Test
    @DisplayName("Decommissioning non-existent SKU throws DomainException")
    void decommissionNonExistentSkuThrows() {
        assertThatThrownBy(() -> decommissionService.decommissionVariant("NON-EXISTENT", "None"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Product family not found");
    }

    private static class TestEventPublisher implements ApplicationEventPublisher {
        final List<DomainEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof DomainEvent de) {
                publishedEvents.add(de);
            }
        }
    }

    private static class InMemoryProductRepository implements ProductRepository {
        private final Map<UUID, ProductFamily> familiesById = new HashMap<>();
        private final Map<String, ProductFamily> familiesBySku = new HashMap<>();

        @Override
        public ProductFamily save(ProductFamily domain) {
            familiesById.put(domain.getId(), domain);
            for (Variant v : domain.getVariants()) {
                familiesBySku.put(v.getSku(), domain);
            }
            return domain;
        }

        @Override
        public Optional<ProductFamily> findById(UUID id) {
            return Optional.ofNullable(familiesById.get(id));
        }

        @Override
        public Optional<ProductFamily> findByCode(String code) {
            return familiesById.values().stream().filter(f -> f.getCode().equalsIgnoreCase(code)).findFirst();
        }

        @Override
        public Optional<ProductFamily> findBySku(String sku) {
            return Optional.ofNullable(familiesBySku.get(sku));
        }

        @Override
        public Optional<ProductFamily> findByVariantId(UUID variantId) {
            return familiesById.values().stream()
                    .filter(f -> f.getVariants().stream().anyMatch(v -> v.getId().equals(variantId)))
                    .findFirst();
        }

        @Override
        public List<ProductFamily> findByStatus(ProductLifecycleState status, int page, int size) {
            return List.of();
        }

        @Override
        public List<ProductFamily> findAll(int page, int size) {
            return new ArrayList<>(familiesById.values());
        }

        @Override
        public void deleteById(UUID id) {
            familiesById.remove(id);
        }

        @Override
        public boolean existsByCode(String code) {
            return findByCode(code).isPresent();
        }
    }
}
