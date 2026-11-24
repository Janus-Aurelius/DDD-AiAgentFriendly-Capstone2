package com.retail.pim.syndication.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade.ChannelFeedbackResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AmazonFeedProcessingReportParser Tests")
class AmazonFeedProcessingReportParserTest {

    private CatalogChannelFeedbackFacade feedbackFacade;
    private ObjectMapper objectMapper;
    private AmazonFeedProcessingReportParser parser;

    @BeforeEach
    void setUp() {
        feedbackFacade = mock(CatalogChannelFeedbackFacade.class);
        objectMapper = new ObjectMapper();
        parser = new AmazonFeedProcessingReportParser(feedbackFacade, objectMapper);
    }

    @Test
    @DisplayName("Should route HTTP 429 feed response to retry queue without reporting rejection")
    void shouldRouteHttp429ToRetryQueue() {
        String reportJson = """
            {
                "feedSubmissionId": "FEED-9901",
                "httpStatusCode": 429,
                "message": "Too Many Requests - Throttled"
            }
            """;

        AmazonFeedProcessingReportParser.FeedProcessingSummary summary =
                parser.parseAndProcessReport("AMAZON_US", reportJson);

        assertEquals("FEED-9901", summary.feedSubmissionId());
        assertEquals(1, summary.throttledCount());
        assertEquals(0, summary.appliedCount());
        verify(feedbackFacade, never()).reportChannelRejection(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("Should parse feed items, routing rate limit errors to retry and valid errors to feedback facade")
    void shouldParseFeedItemsAndRouteCorrectly() {
        String reportJson = """
            {
                "feedSubmissionId": "FEED-9902",
                "httpStatusCode": 200,
                "items": [
                    {
                        "sku": "SKU-TV",
                        "evaluatedVersion": 1,
                        "status": "ERROR",
                        "errorCode": "INVALID_IMAGE_URL",
                        "errorMessage": "Image URL unreachable"
                    },
                    {
                        "sku": "SKU-AUDIO",
                        "evaluatedVersion": 3,
                        "status": "ERROR",
                        "errorCode": "RequestThrottled",
                        "errorMessage": "Rate limit exceeded"
                    },
                    {
                        "sku": "SKU-CABLE",
                        "evaluatedVersion": 2,
                        "status": "SUCCESS"
                    }
                ]
            }
            """;

        when(feedbackFacade.reportChannelRejection("SKU-TV", "AMAZON_US", 1L, "INVALID_IMAGE_URL: Image URL unreachable"))
                .thenReturn(ChannelFeedbackResult.DISCARDED_STALE);
        when(feedbackFacade.reportChannelSuccess("SKU-CABLE", "AMAZON_US", 2L))
                .thenReturn(ChannelFeedbackResult.APPLIED);

        AmazonFeedProcessingReportParser.FeedProcessingSummary summary =
                parser.parseAndProcessReport("AMAZON_US", reportJson);

        assertEquals(3, summary.totalItems());
        assertEquals(1, summary.throttledCount(), "SKU-AUDIO with RequestThrottled must be counted as throttled");
        assertEquals(1, summary.staleDiscardedCount(), "SKU-TV stale rejection must be counted");
        assertEquals(1, summary.appliedCount(), "SKU-CABLE success must be applied");

        verify(feedbackFacade).reportChannelRejection("SKU-TV", "AMAZON_US", 1L, "INVALID_IMAGE_URL: Image URL unreachable");
        verify(feedbackFacade, never()).reportChannelRejection(eq("SKU-AUDIO"), any(), anyLong(), any());
        verify(feedbackFacade).reportChannelSuccess("SKU-CABLE", "AMAZON_US", 2L);
    }
}
