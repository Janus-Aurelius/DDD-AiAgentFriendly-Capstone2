package com.retail.pim.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.facades.CatalogRecallFacade;
import com.retail.pim.catalog.application.facades.CatalogRecallFacadeImpl;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.compliance.application.ports.KafkaQosPort;
import com.retail.pim.compliance.application.services.EmergencyRecallService;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import com.retail.pim.compliance.infrastructure.messaging.KafkaQosEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CUJ 2: Class 1 Safety Recall & Kafka QoS Event Dispatch Integration Tests")
class RecallQosDispatchTest {

    private ProductRepository productRepository;
    private CatalogRecallFacade catalogRecallFacade;
    private KafkaOperations<String, String> kafkaOperations;
    private KafkaQosPort kafkaQosPort;
    private EmergencyRecallService recallService;
    private ObjectMapper objectMapper;

    private ProductFamily formulaFamily;
    private Variant formulaVariant;
    private final String sku = "SKU-INF";
    private final String lotNumber = "LOT-9042";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productRepository = mock(ProductRepository.class);
        catalogRecallFacade = new CatalogRecallFacadeImpl(productRepository);

        kafkaOperations = mock(KafkaOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        kafkaQosPort = new KafkaQosEventPublisher(kafkaOperations, objectMapper);

        recallService = new EmergencyRecallService(catalogRecallFacade, kafkaQosPort);

        // Given: Infant Formula Batch Lot #9042 of SKU-INF is active and distributed across warehouse RDCs
        UUID categoryId = UUID.randomUUID();
        formulaFamily = ProductFamily.createDraft("PF-INFANT-NUTRITION", "Infant Formula Complete", "Baby nutrition formula", categoryId, Map.of());

        Dimensions dims = new Dimensions(BigDecimal.valueOf(15), BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.valueOf(0.9), "cm/kg");
        formulaVariant = new Variant(UUID.randomUUID(), sku, "Infant Formula Powder 900g", BigDecimal.valueOf(39.99), "USD", dims, Map.of(), List.of(), true);
        formulaFamily.addVariant(formulaVariant);

        assertEquals(VariantLifecycleState.SKU_ACTIVE, formulaVariant.getLifecycleState());
        assertTrue(formulaVariant.isActive());

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(formulaFamily));
        when(productRepository.save(any(ProductFamily.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("CUJ 2: Declaring FDA Class 1 Safety Recall must quarantine variant and dispatch QoS event with zero database wait")
    void shouldExecuteClass1SafetyRecallEndToEnd() throws Exception {
        // When: Quality Assurance identifies bacterial contamination, declaring emergency recall
        String severity = "CLASS_1_CRITICAL_SAFETY";
        String reason = "Bacterial contamination (Cronobacter sakazakii) detected in Lot #9042";

        ProductLotRecallInitiatedEvent recallEvent = recallService.initiateRecall(sku, lotNumber, severity, reason);

        // Then 1: PIM immediately transitions SKU-INF to SKU_RECALLED_LOT_HOLD
        assertEquals(VariantLifecycleState.SKU_RECALLED_LOT_HOLD, formulaVariant.getLifecycleState());
        assertFalse(formulaVariant.isActive(), "Quarantined SKU must be deactivated from active sales");
        assertFalse(formulaVariant.isRegionallyCompliant("GLOBAL"), "Global safety restriction must be applied");
        assertTrue(formulaVariant.getActiveSuspensionReasons().stream().anyMatch(r -> r.contains("LOT-9042")),
                "Suspension reasons must track the recalled lot number");

        // Verify product family state persistence
        verify(productRepository).save(formulaFamily);

        // Then 2: ProductLotRecallInitiatedEvent is dispatched directly to Kafka QoS Express Lane
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaOperations).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("compliance.report.events.qos", topicCaptor.getValue(),
                "Must publish to dedicated high-priority QoS Express Lane topic");
        assertEquals("SKU-INF", keyCaptor.getValue(),
                "Kafka message key must be target SKU for partitioned ordering");

        // Then 3: Verify payload integrity for downstream 3-Zone Point of No Return (PONR) fulfillment contract
        JsonNode jsonNode = objectMapper.readTree(payloadCaptor.getValue());
        assertEquals(recallEvent.eventId().toString(), jsonNode.get("eventId").asText());
        assertEquals("SKU-INF", jsonNode.get("sku").asText());
        assertEquals("LOT-9042", jsonNode.get("lotNumber").asText());
        assertEquals("CLASS_1_CRITICAL_SAFETY", jsonNode.get("severity").asText());
        assertTrue(jsonNode.get("reason").asText().contains("Cronobacter sakazakii"));
        assertNotNull(jsonNode.get("occurredOn").asText());
    }
}
