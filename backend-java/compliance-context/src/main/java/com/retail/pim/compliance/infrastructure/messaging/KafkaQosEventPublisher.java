package com.retail.pim.compliance.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.compliance.application.ports.KafkaQosPort;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

/**
 * Publishes critical emergency recall events immediately to the dedicated Kafka QoS Express Lane
 * (compliance.report.events.qos) with zero database wait.
 */
@Component
public class KafkaQosEventPublisher implements KafkaQosPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaQosEventPublisher.class);
    public static final String QOS_TOPIC = "compliance.report.events.qos";

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public KafkaQosEventPublisher(@Autowired(required = false) KafkaOperations<String, String> kafkaOperations,
                                  ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishRecallQosEvent(ProductLotRecallInitiatedEvent event) {
        if (event == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            if (kafkaOperations != null) {
                kafkaOperations.send(QOS_TOPIC, event.sku(), payload);
                log.info("Dispatched emergency recall event to Kafka QoS Express Lane [{}] for SKU [{}] Lot [{}]",
                        QOS_TOPIC, event.sku(), event.lotNumber());
            } else {
                log.warn("KafkaOperations bean not present. Simulated dispatch to QoS Express Lane [{}] for SKU [{}]",
                        QOS_TOPIC, event.sku());
            }
        } catch (Exception e) {
            log.error("Failed to serialize or dispatch recall event to Kafka QoS Express Lane: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka QoS dispatch failed for recall event: " + event.eventId(), e);
        }
    }
}
