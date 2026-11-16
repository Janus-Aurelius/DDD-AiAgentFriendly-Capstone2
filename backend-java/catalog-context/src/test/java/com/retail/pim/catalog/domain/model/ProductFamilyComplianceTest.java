package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.domain.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductFamily Aggregate Non-Blocking Compliance Tests")
class ProductFamilyComplianceTest {

    @Test
    @DisplayName("ProductFamily should not throw exception during approve() even if electronics is missing compliance data")
    void shouldApproveElectronicsWithoutHardcodedComplianceCheck() {
        UUID categoryId = UUID.randomUUID();
        // Product marked as electronics, but WITHOUT energy_rating
        Map<String, AttributeValue> attrs = Map.of(
                "is_electronics", AttributeValue.ofBoolean(true),
                "family_type", AttributeValue.ofString("electronics")
        );

        ProductFamily product = ProductFamily.createDraft("PROD-TEST-001", "Test TV", "Smart OLED TV", categoryId, attrs);

        // Add physical SKU variant with weight and dimensions
        Dimensions dims = new Dimensions(BigDecimal.valueOf(100), BigDecimal.valueOf(60), BigDecimal.valueOf(10), BigDecimal.valueOf(15), "cm/kg");
        Variant variant = new Variant(UUID.randomUUID(), "SKU-TV-01", "OLED TV 55", BigDecimal.valueOf(999.99), "USD", dims, Map.of(), List.of(), true);
        product.addVariant(variant);

        // Enrich with localized description
        product.enrich(Map.of("en", "English description"), Map.of());
        assertEquals(ProductLifecycleState.ENRICHED, product.getStatus());

        // Approval must succeed non-blockingly without throwing DomainException for missing energy_rating
        assertDoesNotThrow(product::approve);
        assertEquals(ProductLifecycleState.APPROVED, product.getStatus());
    }

    @Test
    @DisplayName("ProductFamily should emit ProductAttributesUpdatedEvent on draft creation, enrichment, and attribute update")
    void shouldEmitProductAttributesUpdatedEvent() {
        UUID categoryId = UUID.randomUUID();
        Map<String, AttributeValue> initialAttrs = Map.of(
                "brand", AttributeValue.ofString("Sony")
        );

        // 1. Creation emits event
        ProductFamily product = ProductFamily.createDraft("PROD-TV-02", "Sony TV", "Bravia TV", categoryId, initialAttrs);
        List<DomainEvent> events1 = product.getDomainEvents();
        assertEquals(1, events1.size());
        assertTrue(events1.get(0) instanceof ProductAttributesUpdatedEvent);
        ProductAttributesUpdatedEvent createdEvent = (ProductAttributesUpdatedEvent) events1.get(0);
        assertEquals(product.getId(), createdEvent.productId());
        assertEquals("Sony", createdEvent.attributes().get("brand").asString());
        product.clearDomainEvents();

        // 2. Enrich with updated attributes emits event
        product.enrich(Map.of("en", "Bravia 4K TV"), Map.of("voltage", AttributeValue.ofString("230V")));
        List<DomainEvent> events2 = product.getDomainEvents();
        assertEquals(1, events2.size());
        assertTrue(events2.get(0) instanceof ProductAttributesUpdatedEvent);
        ProductAttributesUpdatedEvent enrichEvent = (ProductAttributesUpdatedEvent) events2.get(0);
        assertEquals("230V", enrichEvent.attributes().get("voltage").asString());
        product.clearDomainEvents();

        // 3. setAttribute emits event
        product.setAttribute("energy_rating", AttributeValue.ofString("A++"));
        List<DomainEvent> events3 = product.getDomainEvents();
        assertEquals(1, events3.size());
        assertTrue(events3.get(0) instanceof ProductAttributesUpdatedEvent);
        ProductAttributesUpdatedEvent setAttrEvent = (ProductAttributesUpdatedEvent) events3.get(0);
        assertEquals("A++", setAttrEvent.attributes().get("energy_rating").asString());
        product.clearDomainEvents();

        // 4. updateAttributes emits event
        product.updateAttributes(Map.of("ce_certified", AttributeValue.ofBoolean(true)));
        List<DomainEvent> events4 = product.getDomainEvents();
        assertEquals(1, events4.size());
        assertTrue(events4.get(0) instanceof ProductAttributesUpdatedEvent);
    }
}
