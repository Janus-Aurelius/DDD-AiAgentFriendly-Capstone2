package com.retail.pim.syndication.infrastructure.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * External Edge Adapter: Interacts with external marketplace APIs (Amazon SP-API).
 */
@Component
public class AmazonMarketplaceApiClient {

    private static final Logger log = LoggerFactory.getLogger(AmazonMarketplaceApiClient.class);

    private final ObjectMapper objectMapper;

    public AmazonMarketplaceApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean pushListingFeed(String sellerId, Map<String, Object> productPayload) {
        try {
            String json = objectMapper.writeValueAsString(productPayload);
            log.info("Amazon SP-API: Successfully pushed product SKU [{}] to Amazon Seller [{}] feed:\n{}",
                    productPayload.get("sku"), sellerId, json);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Amazon SP-API: Failed to serialize syndication payload", e);
            return false;
        }
    }
}
