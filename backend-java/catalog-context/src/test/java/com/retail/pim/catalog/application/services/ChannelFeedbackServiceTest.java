package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade.ChannelFeedbackResult;
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

@DisplayName("ChannelFeedbackService Omnichannel Anti-Thrashing Tests")
class ChannelFeedbackServiceTest {

    private ProductRepository productRepository;
    private ChannelFeedbackService feedbackService;

    private ProductFamily productFamily;
    private Variant variant;
    private final String sku = "SKU-TV-4K";
    private final String channel = "AMAZON_US";

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        feedbackService = new ChannelFeedbackService(productRepository);

        UUID categoryId = UUID.randomUUID();
        productFamily = ProductFamily.createDraft("PF-TV", "4K OLED TV", "High end television", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.valueOf(120), BigDecimal.valueOf(70), BigDecimal.valueOf(10), BigDecimal.valueOf(20), "cm/kg");
        variant = new Variant(UUID.randomUUID(), sku, "4K OLED TV 65 Inch", BigDecimal.valueOf(1499.99), "USD", dims, Map.of(), List.of(), true);
        variant.setPublishedVersion(2L); // Variant is currently at publishedVersion = 2
        productFamily.addVariant(variant);

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(productFamily));
        when(productRepository.save(any(ProductFamily.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Should discard stale rejection when evaluatedVersion (1) < publishedVersion (2)")
    void shouldDiscardStaleRejection() {
        // Amazon feed processor asynchronously reports rejection for older Version 1
        long evaluatedVersion = 1L;
        String errorMsg = "Missing required attribute: energy_efficiency_rating";

        ChannelFeedbackResult result = feedbackService.processRejection(sku, channel, evaluatedVersion, errorMsg);

        // Verification: Must discard as stale
        assertEquals(ChannelFeedbackResult.DISCARDED_STALE, result);

        // Variant channel status must NOT be modified
        assertNull(variant.getChannelStatusMap().get(channel), "Channel status must remain untouched on stale rejection");
        assertTrue(variant.getActiveSuspensionReasons().isEmpty(), "No suspension reasons should be added");

        // Product family must NOT be saved
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should apply rejection when evaluatedVersion (2) == publishedVersion (2)")
    void shouldApplyRejectionOnCurrentVersion() {
        // Amazon feed processor reports rejection for current Version 2
        long evaluatedVersion = 2L;
        String errorMsg = "Invalid ASIN brand authorization";

        ChannelFeedbackResult result = feedbackService.processRejection(sku, channel, evaluatedVersion, errorMsg);

        assertEquals(ChannelFeedbackResult.APPLIED, result);

        // Variant channel status for AMAZON_US must be RESTRICTED
        assertEquals("RESTRICTED", variant.getChannelStatusMap().get(channel));
        assertTrue(variant.getActiveSuspensionReasons().stream().anyMatch(r -> r.contains("CHANNEL_REJECTION") && r.contains("AMAZON_US")));

        verify(productRepository).save(productFamily);
    }

    @Test
    @DisplayName("Should apply channel success when evaluatedVersion >= publishedVersion")
    void shouldApplyChannelSuccess() {
        variant.applyChannelStatus(channel, "RESTRICTED");

        ChannelFeedbackResult result = feedbackService.processSuccess(sku, channel, 2L);

        assertEquals(ChannelFeedbackResult.APPLIED, result);
        assertEquals("ACTIVE", variant.getChannelStatusMap().get(channel));
        verify(productRepository).save(productFamily);
    }

    @Test
    @DisplayName("Should discard channel success when evaluatedVersion < publishedVersion")
    void shouldDiscardStaleSuccess() {
        ChannelFeedbackResult result = feedbackService.processSuccess(sku, channel, 1L);

        assertEquals(ChannelFeedbackResult.DISCARDED_STALE, result);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should validate parameters and handle missing SKU")
    void shouldValidateParameters() {
        assertThrows(IllegalArgumentException.class, () -> feedbackService.processRejection("", channel, 2L, "Error"));
        assertThrows(IllegalArgumentException.class, () -> feedbackService.processRejection(sku, "", 2L, "Error"));

        when(productRepository.findBySku("NON-EXISTENT")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> feedbackService.processRejection("NON-EXISTENT", channel, 2L, "Error"));
    }
}
