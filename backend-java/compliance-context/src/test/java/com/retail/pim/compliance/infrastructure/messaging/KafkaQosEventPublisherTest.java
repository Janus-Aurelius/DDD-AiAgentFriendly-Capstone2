package com.retail.pim.compliance.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("KafkaQosEventPublisher Infrastructure Messaging Tests")
class KafkaQosEventPublisherTest {

    private KafkaOperations<String, String> kafkaOperations;
    private ObjectMapper objectMapper;
    private KafkaQosEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaOperations = mock(KafkaOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        publisher = new KafkaQosEventPublisher(kafkaOperations, objectMapper);
    }

    @Test
    @DisplayName("Should publish recall event JSON to compliance.report.events.qos with SKU as message key")
    void shouldPublishRecallEventToQosTopic() throws Exception {
        ProductLotRecallInitiatedEvent event = new ProductLotRecallInitiatedEvent(
                "SKU-INF",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination detected"
        );

        publisher.publishRecallQosEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaOperations).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("compliance.report.events.qos", topicCaptor.getValue());
        assertEquals("SKU-INF", keyCaptor.getValue());

        JsonNode jsonNode = objectMapper.readTree(payloadCaptor.getValue());
        assertEquals("SKU-INF", jsonNode.get("sku").asText());
        assertEquals("LOT-9042", jsonNode.get("lotNumber").asText());
        assertEquals("CLASS_1_CRITICAL_SAFETY", jsonNode.get("severity").asText());
        assertEquals("Bacterial contamination detected", jsonNode.get("reason").asText());
    }

    @Test
    @DisplayName("Should handle null event safely without throwing exception")
    void shouldHandleNullEventSafely() {
        assertDoesNotThrow(() -> publisher.publishRecallQosEvent(null));
        verify(kafkaOperations, never()).send(any(), any(), any());
    }
}
