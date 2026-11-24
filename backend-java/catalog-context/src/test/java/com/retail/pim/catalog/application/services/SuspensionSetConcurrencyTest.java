package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Slice 6: Suspension Set Concurrency & Optimistic Lock Tests (CUJ 6)")
class SuspensionSetConcurrencyTest {

    private InMemoryTestProductRepository productRepository;
    private FamilyHoldManagementService holdService;
    private VariantStateManagementService variantStateService;

    private UUID familyId;
    private ProductFamily testFamily;
    private Variant variantSmall;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryTestProductRepository();
        holdService = new FamilyHoldManagementService(productRepository);
        variantStateService = new VariantStateManagementService(productRepository);

        familyId = UUID.randomUUID();
        testFamily = new ProductFamily(
                familyId, "FAM-POLO", "Classic Polo", "Cotton polo shirt",
                Map.of("en", "Cotton polo shirt"), UUID.randomUUID(),
                ProductLifecycleState.PUBLISHED, Map.of(), null, 1, 10L,
                Collections.emptySet(), FamilyLifecycleState.FAMILY_PUBLISHED
        );

        variantSmall = new Variant(UUID.randomUUID(), "SKU-POLO-S", "Polo Small", new BigDecimal("39.99"), "USD", null, null, null, true);
        variantSmall.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);
        testFamily.addVariant(variantSmall);

        productRepository.save(testFamily);
    }

    @Test
    @DisplayName("Multiple distinct holds coexist concurrently in activeSuspensionReasons")
    void multipleHoldsCoexistConcurrently() {
        // Apply 3 distinct holds
        holdService.applyHold(familyId, "TRADEMARK_DISPUTE", 10L);
        holdService.applyHold(familyId, "REGULATORY_INVESTIGATION", null);
        holdService.applyHold(familyId, "MERCHANDISING_PAUSE", null);

        Set<String> activeHolds = holdService.getActiveHolds(familyId);
        assertThat(activeHolds).containsExactlyInAnyOrder(
                "TRADEMARK_DISPUTE", "REGULATORY_INVESTIGATION", "MERCHANDISING_PAUSE"
        );

        ProductFamily family = productRepository.findById(familyId).orElseThrow();
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);

        // Lifting 1 hold preserves the other 2
        holdService.liftHold(familyId, "REGULATORY_INVESTIGATION", null);
        Set<String> remainingHolds = holdService.getActiveHolds(familyId);
        assertThat(remainingHolds).containsExactlyInAnyOrder("TRADEMARK_DISPUTE", "MERCHANDISING_PAUSE");
        assertThat(productRepository.findById(familyId).orElseThrow().getLifecycleState())
                .isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);
    }

    @Test
    @DisplayName("Optimistic lock: Mismatched expectedVersion throws DomainException preventing lost update")
    void optimisticLockPreventsLostUpdates() {
        // Attempting to apply hold with stale version (expected 5, but aggregate is at 10)
        assertThatThrownBy(() -> holdService.applyHold(familyId, "LEGAL_HOLD", 5L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Optimistic lock conflict");

        // Attempting to lift hold with stale version
        assertThatThrownBy(() -> holdService.liftHold(familyId, "LEGAL_HOLD", 9L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Optimistic lock conflict");
    }

    @Test
    @DisplayName("VariantStateManagementService: Suspending sole variant auto-suspends parent family")
    void variantServiceAutoSuspendsParentFamily() {
        variantStateService.suspendVariant("SKU-POLO-S", "DEFECTIVE_BUTTONS");

        ProductFamily family = productRepository.findById(familyId).orElseThrow();
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);
        assertThat(family.getActiveSuspensionReasons()).contains("NO_ACTIVE_VARIANTS");

        // Restoring variant restores family
        variantStateService.restoreVariant("SKU-POLO-S", "DEFECTIVE_BUTTONS");

        ProductFamily restoredFamily = productRepository.findById(familyId).orElseThrow();
        assertThat(restoredFamily.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
        assertThat(restoredFamily.getActiveSuspensionReasons()).isEmpty();
    }

    private static class InMemoryTestProductRepository implements ProductRepository {
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
