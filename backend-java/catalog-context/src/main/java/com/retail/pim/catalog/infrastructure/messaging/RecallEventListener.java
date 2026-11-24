package com.retail.pim.catalog.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogRecallFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to high-priority safety recall events on the Kafka QoS Express Lane
 * and idempotently applies quarantine holds to the target variant.
 */
@Component
public class RecallEventListener {

    private static final Logger log = LoggerFactory.getLogger(RecallEventListener.class);
    private static final String CONSUMER_GROUP = "pim-catalog-recall-consumer";

    private final CatalogRecallFacade recallFacade;
    private final ObjectMapper objectMapper;
    private final IdempotentConsumerGuard idempotentGuard;

    public RecallEventListener(CatalogRecallFacade recallFacade,
                               ObjectMapper objectMapper,
                               IdempotentConsumerGuard idempotentGuard) {
        this.recallFacade = recallFacade;
        this.objectMapper = objectMapper;
        this.idempotentGuard = idempotentGuard;
    }

    public record RecallEventPayload(
            UUID eventId,
            String sku,
            String lotNumber,
            String severity,
            String reason
    ) {}

    @KafkaListener(topics = "compliance.report.events.qos", groupId = CONSUMER_GROUP)
    public void onRecallEvent(String messagePayload) {
        try {
            RecallEventPayload event = objectMapper.readValue(messagePayload, RecallEventPayload.class);
            UUID eventId = event.eventId() != null ? event.eventId() : UUID.nameUUIDFromBytes(messagePayload.getBytes());

            boolean isNew = idempotentGuard.tryConsume(eventId, CONSUMER_GROUP);
            if (!isNew) {
                log.info("Recall event [{}] for SKU [{}] already processed. Skipping duplicate.", eventId, event.sku());
                return;
            }

            log.warn("Received emergency recall event on QoS Express Lane for SKU [{}] Lot [{}]. Quarantining...",
                    event.sku(), event.lotNumber());
            recallFacade.quarantineVariant(event.sku(), event.lotNumber(), event.reason());
        } catch (Exception e) {
            log.error("Failed to process emergency recall message from QoS Express Lane: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing recall event", e);
        }
    }
}
