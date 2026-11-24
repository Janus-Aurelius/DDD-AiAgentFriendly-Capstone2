package com.retail.pim.syndication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacadeImpl;
import com.retail.pim.catalog.application.services.ChannelFeedbackService;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.syndication.application.services.AmazonFeedProcessingReportParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CUJ 4: Omnichannel Feed Resilience & Anti-Thrashing Integration Tests")
class ChannelFeedbackAntiThrashingTest {

    private ProductRepository productRepository;
    private ChannelFeedbackService feedbackService;
    private CatalogChannelFeedbackFacade feedbackFacade;
    private AmazonFeedProcessingReportParser reportParser;
    private ObjectMapper objectMapper;

    private ProductFamily tvFamily;
    private Variant tvVariant;
    private final String sku = "SKU-TV-4K";
    private final String channel = "AMAZON_US";

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        feedbackService = new ChannelFeedbackService(productRepository);
        feedbackFacade = new CatalogChannelFeedbackFacadeImpl(feedbackService);
        objectMapper = new ObjectMapper();
        reportParser = new AmazonFeedProcessingReportParser(feedbackFacade, objectMapper);

        // Given: 4K Television (SKU-TV) where version was incremented from 1 to 2
        UUID categoryId = UUID.randomUUID();
        tvFamily = ProductFamily.createDraft("PF-TV-4K", "Ultra HD 4K OLED TV", "High end television", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.valueOf(120), BigDecimal.valueOf(70), BigDecimal.valueOf(10), BigDecimal.valueOf(20), "cm/kg");
        tvVariant = new Variant(UUID.randomUUID(), sku, "Ultra HD 4K OLED TV 65 Inch", BigDecimal.valueOf(1999.99), "USD", dims, Map.of(), List.of(), true);

        // Version 2 is actively publishing, active on Shopify
        tvVariant.setPublishedVersion(2L);
        tvVariant.applyChannelStatus("SHOPIFY", "ACTIVE");
        tvFamily.addVariant(tvVariant);

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(tvFamily));
        when(productRepository.save(any(ProductFamily.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("CUJ 4: Stale rejection on Version 1 must be discarded without thrashing actively publishing Version 2")
    void shouldPreventThrashingOnStaleRejectionAndApplyTerminalRejectionOnCurrentVersion() {
        // When 1: Amazon's asynchronous feed processor rejects older Version 1 due to schema error
        String staleReportJson = String.format("""
            {
                "feedSubmissionId": "FEED-SUBMISSION-001",
                "httpStatusCode": 200,
                "items": [
                    {
                        "sku": "%s",
                        "evaluatedVersion": 1,
                        "status": "ERROR",
                        "errorCode": "SCHEMA_VALIDATION_ERROR",
                        "errorMessage": "Old attribute structure invalid in Amazon SP-API"
                    }
                ]
            }
            """, sku);

        AmazonFeedProcessingReportParser.FeedProcessingSummary staleSummary =
                reportParser.parseAndProcessReport(channel, staleReportJson);

        // Then 1: Rejection must be discarded as stale (1 < 2)
        assertEquals(1, staleSummary.staleDiscardedCount(), "Rejection for version 1 must be discarded as stale");
        assertEquals(0, staleSummary.appliedCount());
        assertNull(tvVariant.getChannelStatusMap().get(channel), "AMAZON_US status must not be modified by stale rejection");
        assertEquals("ACTIVE", tvVariant.getChannelStatusMap().get("SHOPIFY"), "SHOPIFY channel must remain unhindered");
        verify(productRepository, never()).save(any());

        // When 2: Amazon later evaluates current Version 2 and rejects with terminal error
        String currentReportJson = String.format("""
            {
                "feedSubmissionId": "FEED-SUBMISSION-002",
                "httpStatusCode": 200,
                "items": [
                    {
                        "sku": "%s",
                        "evaluatedVersion": 2,
                        "status": "ERROR",
                        "errorCode": "BRAND_AUTH_REQUIRED",
                        "errorMessage": "Brand letter of authorization missing"
                    }
                ]
            }
            """, sku);

        AmazonFeedProcessingReportParser.FeedProcessingSummary currentSummary =
                reportParser.parseAndProcessReport(channel, currentReportJson);

        // Then 2: Current rejection (2 == 2) must be applied
        assertEquals(1, currentSummary.appliedCount());
        assertEquals(0, currentSummary.staleDiscardedCount());

        // AMAZON_US channel is restricted, but SHOPIFY remains unhindered
        assertEquals("RESTRICTED", tvVariant.getChannelStatusMap().get(channel));
        assertEquals("ACTIVE", tvVariant.getChannelStatusMap().get("SHOPIFY"),
                "Omnichannel isolation: restriction on Amazon must NOT affect Shopify");
        assertTrue(tvVariant.getActiveSuspensionReasons().stream().anyMatch(r -> r.contains("AMAZON_US") && r.contains("BRAND_AUTH_REQUIRED")));

        verify(productRepository).save(tvFamily);
    }
}
