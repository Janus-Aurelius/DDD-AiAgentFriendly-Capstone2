package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Slice 6: Product Family State Machine & Last Variant Standing Tests (CUJ 6)")
class ProductFamilyStateMachineTest {

    @Test
    @DisplayName("Last Variant Standing: Family auto-suspends when active variants drop to 0 and recovers when restored")
    void lastVariantStandingAutoSuspensionAndRecovery() {
        ProductFamily family = new ProductFamily(
                UUID.randomUUID(), "FAM-POLO", "Classic Polo Shirt", "Premium cotton polo",
                Map.of("en", "Premium cotton polo"), UUID.randomUUID(),
                ProductLifecycleState.PUBLISHED, Map.of(), null, 1, 1L,
                Collections.emptySet(), FamilyLifecycleState.FAMILY_PUBLISHED
        );

        // 3 variants: SKU-BL (suspended), SKU-RD (suspended), SKU-SM (active)
        Variant varBlue = new Variant(UUID.randomUUID(), "SKU-BL", "Polo Blue", new BigDecimal("49.99"), "USD", null, null, null, false);
        varBlue.setLifecycleState(VariantLifecycleState.SKU_SUSPENDED);

        Variant varRed = new Variant(UUID.randomUUID(), "SKU-RD", "Polo Red", new BigDecimal("49.99"), "USD", null, null, null, false);
        varRed.setLifecycleState(VariantLifecycleState.SKU_SUSPENDED);

        Variant varSmallBlack = new Variant(UUID.randomUUID(), "SKU-SM", "Polo Small Black", new BigDecimal("49.99"), "USD", null, null, null, true);
        varSmallBlack.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);

        family.addVariant(varBlue);
        family.addVariant(varRed);
        family.addVariant(varSmallBlack);

        assertThat(family.countActiveVariants()).isEqualTo(1);
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
        assertThat(family.getActiveSuspensionReasons()).isEmpty();

        // When: Small Black variant transitions out of SKU_ACTIVE
        varSmallBlack.transitionTo(VariantLifecycleState.SKU_SUSPENDED);
        family.evaluateVariantCount(family.countActiveVariants());

        // Then: active count is 0 -> Family is FAMILY_SUSPENDED with NO_ACTIVE_VARIANTS
        assertThat(family.countActiveVariants()).isEqualTo(0);
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);
        assertThat(family.getActiveSuspensionReasons()).containsExactly("NO_ACTIVE_VARIANTS");

        // When: Merchandiser restores Small Black to SKU_ACTIVE
        varSmallBlack.transitionTo(VariantLifecycleState.SKU_ACTIVE);
        family.evaluateVariantCount(family.countActiveVariants());

        // Then: NO_ACTIVE_VARIANTS is cleared and family returns to FAMILY_PUBLISHED
        assertThat(family.countActiveVariants()).isEqualTo(1);
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
        assertThat(family.getActiveSuspensionReasons()).isEmpty();
    }

    @Test
    @DisplayName("Hold Coexistence: Legal hold prevents automated variant recovery from prematurely restoring family status")
    void legalHoldPreventsPrematureRecovery() {
        ProductFamily family = new ProductFamily(
                UUID.randomUUID(), "FAM-POLO", "Classic Polo Shirt", "Premium cotton polo",
                Map.of("en", "Premium cotton polo"), UUID.randomUUID(),
                ProductLifecycleState.PUBLISHED, Map.of(), null, 1, 1L,
                Collections.emptySet(), FamilyLifecycleState.FAMILY_PUBLISHED
        );

        Variant varSmallBlack = new Variant(UUID.randomUUID(), "SKU-SM", "Polo Small Black", new BigDecimal("49.99"), "USD", null, null, null, true);
        varSmallBlack.setLifecycleState(VariantLifecycleState.SKU_ACTIVE);
        family.addVariant(varSmallBlack);

        // Step 1: Active variants drop to 0 -> Family auto-suspended
        varSmallBlack.transitionTo(VariantLifecycleState.SKU_SUSPENDED);
        family.evaluateVariantCount(family.countActiveVariants());
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);
        assertThat(family.getActiveSuspensionReasons()).containsExactly("NO_ACTIVE_VARIANTS");

        // Step 2: Legal concurrently places explicit TRADEMARK_DISPUTE hold
        family.applyHold("TRADEMARK_DISPUTE");
        assertThat(family.getActiveSuspensionReasons())
                .containsExactlyInAnyOrder("NO_ACTIVE_VARIANTS", "TRADEMARK_DISPUTE");
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);

        // Step 3: Merchandisers restore SKU-SM to SKU_ACTIVE
        varSmallBlack.transitionTo(VariantLifecycleState.SKU_ACTIVE);
        family.evaluateVariantCount(family.countActiveVariants());

        // Then: NO_ACTIVE_VARIANTS is cleared, but TRADEMARK_DISPUTE remains -> family REMAINS FAMILY_SUSPENDED!
        assertThat(family.getActiveSuspensionReasons()).containsExactly("TRADEMARK_DISPUTE");
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_SUSPENDED);

        // Step 4: Legal resolves dispute and lifts TRADEMARK_DISPUTE
        family.liftHold("TRADEMARK_DISPUTE");

        // Then: All holds cleared and activeVariants > 0 -> Family restored to FAMILY_PUBLISHED
        assertThat(family.getActiveSuspensionReasons()).isEmpty();
        assertThat(family.getLifecycleState()).isEqualTo(FamilyLifecycleState.FAMILY_PUBLISHED);
    }

    @Test
    @DisplayName("Variant FSM: Enforces guarded state transitions across all 8 lifecycle states")
    void variantFsmTransitionGuards() {
        Variant variant = new Variant(UUID.randomUUID(), "SKU-TEST-01", "Test Item", new BigDecimal("10.00"), "USD", null, null, null, false);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_DRAFT);

        // SKU_DRAFT -> SKU_IN_REVIEW -> SKU_ACTIVE
        variant.transitionTo(VariantLifecycleState.SKU_IN_REVIEW);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_IN_REVIEW);

        variant.transitionTo(VariantLifecycleState.SKU_ACTIVE);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_ACTIVE);
        assertThat(variant.isActive()).isTrue();

        // SKU_ACTIVE -> SKU_RESTRICTED -> SKU_IN_REVIEW
        variant.transitionTo(VariantLifecycleState.SKU_RESTRICTED);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_RESTRICTED);
        assertThat(variant.isActive()).isTrue();

        variant.transitionTo(VariantLifecycleState.SKU_IN_REVIEW);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_IN_REVIEW);

        // Back to active then recall lot hold
        variant.transitionTo(VariantLifecycleState.SKU_ACTIVE);
        variant.transitionTo(VariantLifecycleState.SKU_RECALLED_LOT_HOLD);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_RECALLED_LOT_HOLD);
        assertThat(variant.isActive()).isFalse();

        // Recall lot hold to discontinued to archived
        variant.transitionTo(VariantLifecycleState.SKU_DISCONTINUED);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_DISCONTINUED);

        variant.transitionTo(VariantLifecycleState.SKU_ARCHIVED);
        assertThat(variant.getLifecycleState()).isEqualTo(VariantLifecycleState.SKU_ARCHIVED);

        // Archived is terminal: transitions must fail
        assertThatThrownBy(() -> variant.transitionTo(VariantLifecycleState.SKU_ACTIVE))
                .isInstanceOf(DomainException.class);
    }
}
