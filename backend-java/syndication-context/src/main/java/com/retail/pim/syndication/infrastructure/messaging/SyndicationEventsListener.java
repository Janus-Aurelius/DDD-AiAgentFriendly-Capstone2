package com.retail.pim.syndication.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.syndication.application.projections.ProductReadModelProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound Messaging Adapter: "The Great Aggregation".
 * Listens to distributed event streams from Localization, Legal Compliance, and Creative DAM
 * to trigger read model denormalization and external channel syndication.
 */
@Component
public class SyndicationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(SyndicationEventsListener.class);

    private final ProductReadModelProjector projector;
    private final ObjectMapper objectMapper;

    public SyndicationEventsListener(ProductReadModelProjector projector, ObjectMapper objectMapper) {
        this.projector = projector;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.regional-events:localization.regional.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-syndication-consumer}"
    )
    public void onRegionalItemPublished(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"RegionalItemPublished".equalsIgnoreCase(root.path("eventType").asText())) {
                return;
            }

            UUID globalProductId = UUID.fromString(root.path("globalProductId").asText());
            String productCode = root.path("productCode").asText();
            String regionCode = root.path("regionCode").asText();
            String localizedTitle = root.path("localizedTitle").asText();

            Map<String, String> overrides = new HashMap<>();
            JsonNode overridesNode = root.path("attributeOverrides");
            if (overridesNode.isObject()) {
                overridesNode.fields().forEachRemaining(entry -> overrides.put(entry.getKey(), entry.getValue().asText()));
            }

            log.info("Syndication Listener: Catching RegionalItemPublished for SKU [{}], Region [{}]", productCode, regionCode);
            projector.projectRegionalItemPublished(globalProductId, productCode, regionCode, localizedTitle, overrides);

        } catch (Exception e) {
            log.error("Failed to process RegionalItemPublished event in Syndication", e);
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.dam-events:dam.asset.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-syndication-dam-consumer}"
    )
    public void onDamAssetLinked(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"AssetLinkedToProduct".equalsIgnoreCase(root.path("eventType").asText())) {
                return;
            }

            String sku = root.path("sku").asText();
            String assetId = root.path("assetId").asText();
            boolean isPrimary = root.path("isPrimaryImage").asBoolean(true);
            String imageUrl = "https://cdn.retail.com/assets/" + assetId + "/web.webp";

            log.info("Syndication Listener: Catching AssetLinkedToProduct for SKU [{}]", sku);
            // Ingest image into US projection by default
            projector.projectAssetLinked(sku, "US", imageUrl, isPrimary);

        } catch (Exception e) {
            log.error("Failed to process AssetLinkedToProduct event in Syndication", e);
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.compliance-events:compliance.report.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-syndication-compliance-consumer}"
    )
    public void onComplianceEvaluated(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"ComplianceEvaluated".equalsIgnoreCase(root.path("eventType").asText())) {
                return;
            }

            String productCode = root.path("productCode").asText();
            if (productCode == null || productCode.isBlank()) {
                productCode = root.path("productId").asText();
            }
            String targetLocale = root.path("targetLocale").asText("US");
            boolean isPublishable = root.hasNonNull("isPublishable")
                    ? root.path("isPublishable").asBoolean()
                    : "COMPLIANT".equalsIgnoreCase(root.path("status").asText());

            log.info("Syndication Listener: Received ComplianceEvaluated for product [{}], Region [{}], publishable={}",
                    productCode, targetLocale, isPublishable);
            projector.projectComplianceCleared(productCode, targetLocale, isPublishable);

        } catch (Exception e) {
            log.error("Failed to process ComplianceEvaluated event in Syndication", e);
        }
    }
}
