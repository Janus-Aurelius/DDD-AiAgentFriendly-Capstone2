package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.RestrictionReason;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CUJ 1: Deterministic Hero Variant Selection and Regional Fallback Tests")
class HeroVariantSelectionTest {

    private ProductFamily productFamily;
    private Variant redVariant;
    private Variant blueVariant;
    private Variant greenVariant;

    @BeforeEach
    void setUp() {
        UUID categoryId = UUID.randomUUID();
        productFamily = ProductFamily.createDraft("PF-TSHIRT", "Classic Crewneck", "Comfortable cotton tee", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.valueOf(30), BigDecimal.valueOf(20), BigDecimal.valueOf(2), BigDecimal.valueOf(0.2), "cm/kg");

        // Primary Hero Variant: Red (Bestseller, Display Priority = 100)
        redVariant = new Variant(
                UUID.randomUUID(),
                "SKU-TSHIRT-RED",
                "Classic Crewneck - Red",
                BigDecimal.valueOf(29.99),
                "USD",
                dims,
                Map.of("color", AttributeValue.ofString("Red")),
                List.of(),
                true // isActive
        );
        redVariant.setBestseller(true);
        redVariant.setDisplayPriority(100);

        // Secondary Variant: Blue (Priority = 50)
        blueVariant = new Variant(
                UUID.randomUUID(),
                "SKU-TSHIRT-BLUE",
                "Classic Crewneck - Blue",
                BigDecimal.valueOf(29.99),
                "USD",
                dims,
                Map.of("color", AttributeValue.ofString("Blue")),
                List.of(),
                true
        );
        blueVariant.setBestseller(false);
        blueVariant.setDisplayPriority(50);

        // Tertiary Variant: Green (Priority = 10)
        greenVariant = new Variant(
                UUID.randomUUID(),
                "SKU-TSHIRT-GREEN",
                "Classic Crewneck - Green",
                BigDecimal.valueOf(29.99),
                "USD",
                dims,
                Map.of("color", AttributeValue.ofString("Green")),
                List.of(),
                true
        );
        greenVariant.setBestseller(false);
        greenVariant.setDisplayPriority(10);

        productFamily.addVariant(redVariant);
        productFamily.addVariant(blueVariant);
        productFamily.addVariant(greenVariant);
    }

    @Test
    @DisplayName("Should select bestseller SKU-RED as hero variant when unrestricted (US_NY or global)")
    void shouldSelectBestsellerHeroVariantWhenUnrestricted() {
        // Global / unspecified locale
        Optional<Variant> globalHero = productFamily.resolveHeroVariant(null);
        assertTrue(globalHero.isPresent());
        assertEquals("SKU-TSHIRT-RED", globalHero.get().getSku());

        // US_NY market where all are compliant
        Optional<Variant> nyHero = productFamily.resolveHeroVariant("US_NY");
        assertTrue(nyHero.isPresent());
        assertEquals("SKU-TSHIRT-RED", nyHero.get().getSku());
    }

    @Test
    @DisplayName("Should deterministically fall back to SKU-BLUE in US_CA when SKU-RED is masked due to California Prop 65")
    void shouldFallbackToCompliantVariantWhenHeroIsMasked() {
        // Apply California Prop 65 regional mask to red variant
        redVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);
        assertFalse(redVariant.isRegionallyCompliant("US_CA"));
        assertTrue(redVariant.isRegionallyCompliant("US_NY"));

        // Query hero variant for US_CA -> must skip masked red variant and select compliant blue variant
        Optional<Variant> caHero = productFamily.resolveHeroVariant("US_CA");
        assertTrue(caHero.isPresent());
        assertEquals("SKU-TSHIRT-BLUE", caHero.get().getSku(),
                "Hero resolution in US_CA must deterministically fall back to the highest priority compliant variant");

        // Query hero variant for US_NY -> red variant remains the hero in unrestricted markets
        Optional<Variant> nyHero = productFamily.resolveHeroVariant("US_NY");
        assertTrue(nyHero.isPresent());
        assertEquals("SKU-TSHIRT-RED", nyHero.get().getSku(),
                "Hero resolution in US_NY must still select SKU-RED");
    }

    @Test
    @DisplayName("Should fall back to tertiary variant if both primary and secondary are masked in US_CA")
    void shouldFallbackToTertiaryVariantWhenPrimaryAndSecondaryMasked() {
        redVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);
        blueVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);

        Optional<Variant> caHero = productFamily.resolveHeroVariant("US_CA");
        assertTrue(caHero.isPresent());
        assertEquals("SKU-TSHIRT-GREEN", caHero.get().getSku());
    }

    @Test
    @DisplayName("Should return empty when all variants are masked in target market")
    void shouldReturnEmptyWhenAllVariantsMasked() {
        redVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);
        blueVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);
        greenVariant.applyRegionalMask("US_CA", RestrictionReason.REGIONAL_COMPLIANCE_HOLD);

        Optional<Variant> caHero = productFamily.resolveHeroVariant("US_CA");
        assertTrue(caHero.isEmpty());
    }

    @Test
    @DisplayName("Should use SKU as deterministic tie-breaker when priority and bestseller flags match")
    void shouldBreakTiesDeterministicallyBySku() {
        Dimensions dims = new Dimensions(BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(1), BigDecimal.valueOf(0.1), "cm/kg");
        Variant variantB = new Variant(UUID.randomUUID(), "SKU-B", "Variant B", BigDecimal.valueOf(10), "USD", dims, Map.of(), List.of(), true);
        Variant variantA = new Variant(UUID.randomUUID(), "SKU-A", "Variant A", BigDecimal.valueOf(10), "USD", dims, Map.of(), List.of(), true);

        variantA.setDisplayPriority(50);
        variantB.setDisplayPriority(50);
        variantA.setBestseller(false);
        variantB.setBestseller(false);

        ProductFamily family = ProductFamily.createDraft("PF-TIE", "Tie Product", "Tie description", UUID.randomUUID(), Map.of());
        family.addVariant(variantB);
        family.addVariant(variantA);

        Optional<Variant> hero = family.resolveHeroVariant(null);
        assertTrue(hero.isPresent());
        assertEquals("SKU-A", hero.get().getSku(), "Alphabetically smaller SKU must win tie-breaker");
    }
}
