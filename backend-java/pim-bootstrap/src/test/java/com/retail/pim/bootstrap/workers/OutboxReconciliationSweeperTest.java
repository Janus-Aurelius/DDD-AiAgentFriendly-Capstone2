package com.retail.pim.bootstrap.workers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxReconciliationSweeperTest {

    private JdbcOperations jdbcTemplate;
    private KafkaOperations<String, String> kafkaTemplate;
    private OutboxReconciliationSweeper sweeper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcOperations.class);
        @SuppressWarnings("unchecked")
        KafkaOperations<String, String> mockKafka = mock(KafkaOperations.class);
        kafkaTemplate = mockKafka;
        sweeper = new OutboxReconciliationSweeper(jdbcTemplate, kafkaTemplate);
    }

    @Test
    @DisplayName("sweep should query stale outbox events across schemas, dispatch them to Kafka, and mark them completed")
    void shouldSweepAndReconcileStaleEvents() {
        UUID eventId = UUID.randomUUID();
        OutboxReconciliationSweeper.ReconciledOutboxEvent event = new OutboxReconciliationSweeper.ReconciledOutboxEvent(
                eventId,
                "compliance",
                "ProductLotRecallInitiatedEvent",
                "{\"sku\":\"SKU-01\"}",
                "compliance.report.events.qos",
                "SKU-01"
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(event))
                .thenReturn(List.of())
                .thenReturn(List.of());

        int count = sweeper.sweep();

        assertEquals(1, count);
        verify(kafkaTemplate, times(1)).send(eq("compliance.report.events.qos"), eq("SKU-01"), eq("{\"sku\":\"SKU-01\"}"));
        verify(jdbcTemplate, times(1)).update(eq("UPDATE compliance.outbox_event SET completed_at = NOW() WHERE id = ?"), eq(eventId));
    }
}
