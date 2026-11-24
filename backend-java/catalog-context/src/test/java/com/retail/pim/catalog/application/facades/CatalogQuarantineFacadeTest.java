package com.retail.pim.catalog.application.facades;

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

@DisplayName("Slice 8: Catalog Quarantine Facade (WMS Exit Bypass) Tests (CUJ 8)")
class CatalogQuarantineFacadeTest {

    private InMemoryProductRepository productRepository;
    private CatalogQuarantineFacade quarantineFacade;

    private UUID familyId;
    private ProductFamily productFamily;
    private Variant recalledVariant;
    private final String sku = "SKU-RECALLED-LOT";
    private final String lot = "LOT-2026-X";

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        quarantineFacade = new CatalogQuarantineFacadeImpl(productRepository);

        familyId = UUID.randomUUID();
        productFamily = new ProductFamily(
                familyId, "FAM-RECALL", "Recall Family", "Quarantine Test Family",
                Map.of("en", "Recall Family"), UUID.randomUUID(),
                ProductLifecycleState.PUBLISHED, Map.of(), null, 1, 1L,
                Collections.emptySet(), FamilyLifecycleState.FAMILY_SUSPENDED
        );

        recalledVariant = new Variant(UUID.randomUUID(), sku, "Recalled Product", new BigDecimal("49.99"), "USD", null, null, null, false);
        recalledVariant.setLifecycleState(VariantLifecycleState.SKU_RECALLED_LOT_HOLD);
        recalledVariant.addSuspensionReason("SAFETY_RECALL_" + lot);
        recalledVariant.addSuspensionReason("CLASS_1_SAFETY_RECALL");

        productFamily.addVariant(recalledVariant);
        productRepository.save(productFamily);
    }

    @Test
    @DisplayName("WMS certification exit bypass restores variant from SKU_RECALLED_LOT_HOLD to SKU_ACTIVE")
    void certifyLotQuarantineRestoresActiveState() {
        assertThat(recalledVariant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_RECALLED_LOT_HOLD);
        assertThat(recalledVariant.isActive()).isFalse();

        Variant restored = quarantineFacade.certifyLotQuarantine(sku, lot);

        assertThat(restored.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_ACTIVE);
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.getActiveSuspensionReasons()).doesNotContain("SAFETY_RECALL_" + lot);
        assertThat(restored.getActiveSuspensionReasons()).doesNotContain("CLASS_1_SAFETY_RECALL");

        // Verify parent product family auto-restores to published since it now has an active variant
        ProductFamily savedFamily = productRepository.findById(familyId).orElseThrow();
        assertThat(savedFamily.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
    }

    @Test
    @DisplayName("Attempting quarantine bypass on a variant not in SKU_RECALLED_LOT_HOLD throws DomainException")
    void certifyNonQuarantinedVariantThrows() {
        recalledVariant.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);
        productRepository.save(productFamily);

        assertThatThrownBy(() -> quarantineFacade.certifyLotQuarantine(sku, lot))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot certify quarantine bypass")
                .hasMessageContaining("must be SKU_RECALLED_LOT_HOLD");
    }

    @Test
    @DisplayName("Quarantine certification on unknown SKU throws DomainException")
    void certifyUnknownSkuThrows() {
        assertThatThrownBy(() -> quarantineFacade.certifyLotQuarantine("NON-EXISTENT", lot))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Product family not found");
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
