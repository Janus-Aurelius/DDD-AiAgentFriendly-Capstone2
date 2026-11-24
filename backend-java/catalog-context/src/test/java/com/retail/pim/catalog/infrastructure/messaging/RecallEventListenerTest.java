package com.retail.pim.catalog.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogRecallFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RecallEventListener Kafka Messaging Adapter Tests")
class RecallEventListenerTest {

    private CatalogRecallFacade recallFacade;
    private ObjectMapper objectMapper;
    private TestIdempotentConsumerGuard idempotentGuard;
    private RecallEventListener listener;

    static class TestIdempotentConsumerGuard extends IdempotentConsumerGuard {
        private boolean shouldConsume = true;

        public TestIdempotentConsumerGuard() {
            super(null);
        }

        @Override
        public boolean tryConsume(UUID eventId, String consumerGroup) {
            return shouldConsume;
        }

        public void setShouldConsume(boolean shouldConsume) {
            this.shouldConsume = shouldConsume;
        }
    }

    @BeforeEach
    void setUp() {
        recallFacade = mock(CatalogRecallFacade.class);
        objectMapper = new ObjectMapper();
        idempotentGuard = new TestIdempotentConsumerGuard();
        listener = new RecallEventListener(recallFacade, objectMapper, idempotentGuard);
    }

    @Test
    @DisplayName("Should quarantine variant when recall event payload is received")
    void shouldQuarantineVariantOnValidRecallMessage() throws Exception {
        UUID eventId = UUID.randomUUID();
        RecallEventListener.RecallEventPayload payload = new RecallEventListener.RecallEventPayload(
                eventId,
                "SKU-INF",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination"
        );
        String json = objectMapper.writeValueAsString(payload);

        idempotentGuard.setShouldConsume(true);

        listener.onRecallEvent(json);

        verify(recallFacade).quarantineVariant("SKU-INF", "LOT-9042", "Bacterial contamination");
    }

    @Test
    @DisplayName("Should skip processing when idempotent guard detects duplicate event")
    void shouldSkipDuplicateRecallMessage() throws Exception {
        UUID eventId = UUID.randomUUID();
        RecallEventListener.RecallEventPayload payload = new RecallEventListener.RecallEventPayload(
                eventId,
                "SKU-INF",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination"
        );
        String json = objectMapper.writeValueAsString(payload);

        idempotentGuard.setShouldConsume(false);

        listener.onRecallEvent(json);

        verify(recallFacade, never()).quarantineVariant(any(), any(), any());
    }
}
