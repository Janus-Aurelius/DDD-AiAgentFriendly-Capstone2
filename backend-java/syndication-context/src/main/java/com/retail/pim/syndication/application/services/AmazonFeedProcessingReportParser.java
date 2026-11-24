package com.retail.pim.syndication.application.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade;
import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade.ChannelFeedbackResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that parses Amazon SP-API asynchronous feed processing reports.
 * Routes HTTP 429 rate limit exceptions to retry queue without altering catalog state,
 * and routes terminal feed rejections through version-pinned anti-thrashing validation.
 */
@Service
public class AmazonFeedProcessingReportParser {

    private static final Logger log = LoggerFactory.getLogger(AmazonFeedProcessingReportParser.class);

    private final CatalogChannelFeedbackFacade feedbackFacade;
    private final ObjectMapper objectMapper;

    public AmazonFeedProcessingReportParser(CatalogChannelFeedbackFacade feedbackFacade,
                                            ObjectMapper objectMapper) {
        this.feedbackFacade = feedbackFacade;
        this.objectMapper = objectMapper;
    }

    public record FeedItemResult(
            String sku,
            long evaluatedVersion,
            String status,
            String errorCode,
            String errorMessage
    ) {
        public boolean isThrottled() {
            return "429".equals(errorCode) ||
                   "RequestThrottled".equalsIgnoreCase(errorCode) ||
                   "RateExceeded".equalsIgnoreCase(errorCode) ||
                   (errorMessage != null && errorMessage.contains("429"));
        }
    }

    public record FeedProcessingSummary(
            String feedSubmissionId,
            String channel,
            int totalItems,
            int appliedCount,
            int staleDiscardedCount,
            int throttledCount
    ) {}

    public FeedProcessingSummary parseAndProcessReport(String channel, String reportJson) {
        try {
            JsonNode root = objectMapper.readTree(reportJson);
            String feedSubmissionId = root.has("feedSubmissionId") ? root.get("feedSubmissionId").asText() : "UNKNOWN";

            // Check if entire feed request was HTTP 429 throttled
            int httpStatus = root.has("httpStatusCode") ? root.get("httpStatusCode").asInt() : 200;
            if (httpStatus == 429) {
                log.warn("Amazon feed submission [{}] received HTTP 429 Too Many Requests. Routing entire feed to exponential retry queue",
                        feedSubmissionId);
                return new FeedProcessingSummary(feedSubmissionId, channel, 0, 0, 0, 1);
            }

            JsonNode itemsNode = root.get("items");
            if (itemsNode == null || !itemsNode.isArray()) {
                return new FeedProcessingSummary(feedSubmissionId, channel, 0, 0, 0, 0);
            }

            int applied = 0;
            int stale = 0;
            int throttled = 0;

            for (JsonNode itemNode : itemsNode) {
                String sku = itemNode.get("sku").asText();
                long evaluatedVersion = itemNode.has("evaluatedVersion") ? itemNode.get("evaluatedVersion").asLong() : 1L;
                String status = itemNode.has("status") ? itemNode.get("status").asText() : "SUCCESS";
                String errorCode = itemNode.has("errorCode") ? itemNode.get("errorCode").asText() : null;
                String errorMessage = itemNode.has("errorMessage") ? itemNode.get("errorMessage").asText() : null;

                FeedItemResult item = new FeedItemResult(sku, evaluatedVersion, status, errorCode, errorMessage);

                if (item.isThrottled()) {
                    throttled++;
                    log.warn("SKU [{}] encountered Amazon rate limiting ({}). Routed to retry queue.", sku, errorCode);
                    continue;
                }

                if ("ERROR".equalsIgnoreCase(status) || errorCode != null) {
                    String diagnostic = (errorCode != null && errorMessage != null)
                            ? errorCode + ": " + errorMessage
                            : (errorMessage != null ? errorMessage : errorCode);
                    ChannelFeedbackResult result = feedbackFacade.reportChannelRejection(
                            sku, channel, evaluatedVersion, diagnostic
                    );
                    if (result == ChannelFeedbackResult.APPLIED) {
                        applied++;
                    } else {
                        stale++;
                    }
                } else if ("SUCCESS".equalsIgnoreCase(status)) {
                    ChannelFeedbackResult result = feedbackFacade.reportChannelSuccess(sku, channel, evaluatedVersion);
                    if (result == ChannelFeedbackResult.APPLIED) {
                        applied++;
                    } else {
                        stale++;
                    }
                }
            }

            return new FeedProcessingSummary(feedSubmissionId, channel, itemsNode.size(), applied, stale, throttled);
        } catch (Exception e) {
            log.error("Failed to parse Amazon feed processing report: {}", e.getMessage(), e);
            throw new RuntimeException("Feed report parsing failed", e);
        }
    }
}
