package com.retail.pim.compliance.application.services;

import com.retail.pim.catalog.application.facades.CatalogRecallFacade;
import com.retail.pim.compliance.application.ports.KafkaQosPort;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EmergencyRecallService Application Orchestration Tests")
class EmergencyRecallServiceTest {

    private CatalogRecallFacade catalogRecallFacade;
    private KafkaQosPort kafkaQosPort;
    private EmergencyRecallService recallService;

    @BeforeEach
    void setUp() {
        catalogRecallFacade = mock(CatalogRecallFacade.class);
        kafkaQosPort = mock(KafkaQosPort.class);
        recallService = new EmergencyRecallService(catalogRecallFacade, kafkaQosPort);
    }

    @Test
    @DisplayName("Should orchestrate recall: quarantine variant and publish QoS event immediately")
    void shouldInitiateRecallSuccessfully() {
        String sku = "SKU-INF";
        String lotNumber = "LOT-9042";
        String severity = "CLASS_1_CRITICAL_SAFETY";
        String reason = "Bacterial contamination detected";

        ProductLotRecallInitiatedEvent event = recallService.initiateRecall(sku, lotNumber, severity, reason);

        assertNotNull(event);
        assertEquals(sku, event.sku());
        assertEquals(lotNumber, event.lotNumber());
        assertEquals(severity, event.severity());
        assertEquals(reason, event.reason());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredOn());

        // 1. Verify in-process variant quarantine hold
        verify(catalogRecallFacade).quarantineVariant(sku, lotNumber, reason);

        // 2. Verify immediate QoS dispatch
        ArgumentCaptor<ProductLotRecallInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(ProductLotRecallInitiatedEvent.class);
        verify(kafkaQosPort).publishRecallQosEvent(eventCaptor.capture());
        assertEquals(event.eventId(), eventCaptor.getValue().eventId());
    }

    @Test
    @DisplayName("Should use default severity when severity is null or blank")
    void shouldUseDefaultSeverity() {
        ProductLotRecallInitiatedEvent event = recallService.initiateRecall("SKU-INF", "LOT-1", null, null);

        assertEquals("CLASS_1_CRITICAL_SAFETY", event.severity());
        assertEquals("Emergency Class 1 Safety Recall", event.reason());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when SKU or lotNumber is missing")
    void shouldValidateMandatoryFields() {
        assertThrows(IllegalArgumentException.class, () ->
                recallService.initiateRecall(null, "LOT-1", "CRITICAL", "Reason"));
        assertThrows(IllegalArgumentException.class, () ->
                recallService.initiateRecall("SKU-1", null, "CRITICAL", "Reason"));
        assertThrows(IllegalArgumentException.class, () ->
                recallService.initiateRecall("   ", "LOT-1", "CRITICAL", "Reason"));
    }
}
