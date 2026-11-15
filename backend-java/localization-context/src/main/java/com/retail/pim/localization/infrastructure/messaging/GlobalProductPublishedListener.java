package com.retail.pim.localization.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.localization.application.services.RegionalCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inbound Messaging Adapter: Intercepts Core Catalog's ProductPublished events.
 * Triggers the regional fan-out workflow to instantiate RegionalCatalogItems for US, CA, and MX.
 */
@Component
public class GlobalProductPublishedListener {

    private static final Logger log = LoggerFactory.getLogger(GlobalProductPublishedListener.class);

    private final RegionalCatalogService regionalService;
    private final ObjectMapper objectMapper;

    public GlobalProductPublishedListener(RegionalCatalogService regionalService, ObjectMapper objectMapper) {
        this.regionalService = regionalService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.catalog-events:catalog.product.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-localization-consumer}"
    )
    public void onGlobalProductPublished(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String eventType = root.path("eventType").asText();

            if (!"ProductPublished".equalsIgnoreCase(eventType)) {
                return;
            }

            UUID productId = UUID.fromString(root.path("productId").asText());
            String productCode = root.path("productCode").asText();

            log.info("Localization Listener: Received Global ProductPublished for product [{}] ({})",
                    productCode, productId);

            // Execute the fan-out
            regionalService.fanOutRegionalItems(productId, productCode);

        } catch (Exception e) {
            log.error("Failed to process Global ProductPublished event in Localization context: {}", rawMessage, e);
        }
    }
}
