package com.retail.pim.catalog.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BundleComponent Domain Model Tests")
class BundleComponentTest {

    @Test
    @DisplayName("Should create valid BundleComponent entity")
    void shouldCreateValidBundleComponent() {
        UUID bundleVariantId = UUID.randomUUID();
        String componentSku = "SKU-CBL-01";
        int qty = 2;

        BundleComponent component = BundleComponent.of(bundleVariantId, componentSku, qty);

        assertNotNull(component.getId());
        assertEquals(bundleVariantId, component.getBundleVariantId());
        assertEquals(componentSku, component.getComponentSku());
        assertEquals(qty, component.getRequiredQty());
        assertNotNull(component.getCreatedAt());
    }

    @Test
    @DisplayName("Should validate invariants: positive quantity, non-null bundle variant, non-blank SKU")
    void shouldValidateInvariants() {
        UUID validId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                BundleComponent.of(null, "SKU-1", 1));

        assertThrows(IllegalArgumentException.class, () ->
                BundleComponent.of(validId, "", 1));

        assertThrows(IllegalArgumentException.class, () ->
                BundleComponent.of(validId, null, 1));

        assertThrows(IllegalArgumentException.class, () ->
                BundleComponent.of(validId, "SKU-1", 0));

        assertThrows(IllegalArgumentException.class, () ->
                BundleComponent.of(validId, "SKU-1", -5));
    }

    @Test
    @DisplayName("Should enforce value equality based on bundleVariantId and componentSku")
    void shouldEnforceValueEquality() {
        UUID bundleVariantId = UUID.randomUUID();
        BundleComponent c1 = BundleComponent.of(bundleVariantId, "SKU-CBL", 1);
        BundleComponent c2 = BundleComponent.of(bundleVariantId, "SKU-CBL", 2);
        BundleComponent c3 = BundleComponent.of(UUID.randomUUID(), "SKU-CBL", 1);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
    }
}
