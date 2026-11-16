package com.retail.pim.compliance.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.compliance.domain.service.ComplianceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound Messaging / Event Adapter:
 * Listens for ProductAttributesUpdatedEvent (both in-process Spring events and Kafka messages)
 * and triggers asynchronous compliance evaluation via ComplianceEngine.
 */
@Component
public class ProductAttributesUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(ProductAttributesUpdatedListener.class);

    private final ComplianceEngine complianceEngine;
    private final ObjectMapper objectMapper;

    public ProductAttributesUpdatedListener(ComplianceEngine complianceEngine, ObjectMapper objectMapper) {
        this.complianceEngine = complianceEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * In-process Spring Domain Event listener for immediate / modular deployment.
     */
    @EventListener
    public void onProductAttributesUpdated(ProductAttributesUpdatedEvent event) {
        log.info("Compliance Listener (Spring Event): Received ProductAttributesUpdatedEvent for product [{}] (v{})",
                event.productId(), event.productVersion());
        try {
            complianceEngine.evaluateProduct(
                    event.productId(),
                    event.productVersion(),
                    event.categoryId(),
                    event.attributes()
            );
        } catch (Exception e) {
            log.error("Error evaluating compliance for product [{}] from Spring Event", event.productId(), e);
        }
    }

    /**
     * Distributed Kafka listener for event-driven microservices architecture.
     */
    @KafkaListener(
            topics = "${kafka.topics.catalog-events:catalog.product.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-compliance-consumer}",
            autoStartup = "${kafka.compliance.listener.enabled:false}"
    )
    public void onKafkaMessageReceived(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String eventType = root.path("eventType").asText();

            if (!"ProductAttributesUpdated".equalsIgnoreCase(eventType)) {
                return;
            }

            UUID productId = UUID.fromString(root.path("productId").asText());
            Long productVersion = root.hasNonNull("productVersion") ? root.path("productVersion").asLong() : 0L;
            UUID categoryId = root.hasNonNull("categoryId") ? UUID.fromString(root.path("categoryId").asText()) : null;

            log.info("Compliance Listener (Kafka): Received ProductAttributesUpdated for product [{}] (v{})", productId, productVersion);

            Map<String, AttributeValue> attributes = new HashMap<>();
            JsonNode attrsNode = root.path("attributes");
            if (attrsNode.isObject()) {
                attrsNode.fields().forEachRemaining(entry -> {
                    JsonNode valNode = entry.getValue();
                    if (valNode.isObject() && valNode.has("type") && valNode.has("value")) {
                        String typeStr = valNode.path("type").asText();
                        AttributeValue.AttributeType type = AttributeValue.AttributeType.valueOf(typeStr.toUpperCase());
                        String unit = valNode.hasNonNull("unit") ? valNode.path("unit").asText() : null;
                        Object val = valNode.path("value").asText();
                        attributes.put(entry.getKey(), new AttributeValue(type, val, unit));
                    } else if (valNode.isBoolean()) {
                        attributes.put(entry.getKey(), AttributeValue.ofBoolean(valNode.asBoolean()));
                    } else if (valNode.isNumber()) {
                        attributes.put(entry.getKey(), AttributeValue.ofNumber(valNode.numberValue(), null));
                    } else {
                        attributes.put(entry.getKey(), AttributeValue.ofString(valNode.asText()));
                    }
                });
            }

            complianceEngine.evaluateProduct(productId, productVersion, categoryId, null, attributes, null, null);

        } catch (Exception e) {
            log.error("Failed to process Kafka ProductAttributesUpdated event: {}", rawMessage, e);
        }
    }
}
