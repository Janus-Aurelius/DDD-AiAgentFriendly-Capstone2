package com.retail.pim.dam.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DamEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DamEventPublisher.class);
    public static final String TOPIC = "dam.asset.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DamEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getEventId().toString(), payload);
            log.info("Kafka DAM: Published event [{}] of type [{}] to topic [{}]",
                    event.getEventId(), event.getEventType(), TOPIC);
        } catch (JsonProcessingException e) {
            log.error("Kafka DAM: Failed to serialize event {}", event.getEventType(), e);
            throw new RuntimeException("Error publishing DAM domain event", e);
        }
    }
}
