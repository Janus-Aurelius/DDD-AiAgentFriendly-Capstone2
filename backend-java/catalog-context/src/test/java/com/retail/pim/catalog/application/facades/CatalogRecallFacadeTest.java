package com.retail.pim.catalog.application.facades;

import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CatalogRecallFacade Application Facade Tests")
class CatalogRecallFacadeTest {

    private ProductRepository productRepository;
    private CatalogRecallFacade recallFacade;

    private ProductFamily productFamily;
    private Variant variant;
    private final String sku = "SKU-INF";
    private final String lotNumber = "LOT-9042";

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        recallFacade = new CatalogRecallFacadeImpl(productRepository);

        UUID categoryId = UUID.randomUUID();
        productFamily = ProductFamily.createDraft("PF-FORMULA", "Infant Formula", "Baby Nutrition", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.valueOf(15), BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.valueOf(0.9), "cm/kg");
        variant = new Variant(UUID.randomUUID(), sku, "Infant Formula Powder 900g", BigDecimal.valueOf(39.99), "USD", dims, Map.of(), List.of(), true);
        productFamily.addVariant(variant);

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(productFamily));
    }

    @Test
    @DisplayName("Should transition variant to SKU_RECALLED_LOT_HOLD and apply safety restriction on recall quarantine")
    void shouldQuarantineVariantSuccessfully() {
        assertEquals(VariantLifecycleState.SKU_ACTIVE, variant.getLifecycleState());
        assertTrue(variant.isActive());

        recallFacade.quarantineVariant(sku, lotNumber, "Bacterial contamination detected");

        assertEquals(VariantLifecycleState.SKU_RECALLED_LOT_HOLD, variant.getLifecycleState());
        assertFalse(variant.isActive(), "Variant must no longer be active during recall hold");
        assertTrue(variant.getActiveSuspensionReasons().stream().anyMatch(r -> r.contains("LOT-9042") || r.contains("Bacterial")),
                "Suspension reasons must capture the recall lot hold");
        assertFalse(variant.isRegionallyCompliant("GLOBAL"), "Safety restriction mask must be applied");

        verify(productRepository).save(productFamily);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when SKU does not exist")
    void shouldThrowExceptionWhenSkuNotFound() {
        when(productRepository.findBySku("UNKNOWN-SKU")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                recallFacade.quarantineVariant("UNKNOWN-SKU", "LOT-999", "Safety recall"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when SKU is blank")
    void shouldThrowExceptionWhenSkuIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                recallFacade.quarantineVariant("", "LOT-999", "Safety recall"));
    }
}
