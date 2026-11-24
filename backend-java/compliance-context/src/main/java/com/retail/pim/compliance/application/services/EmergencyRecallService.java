package com.retail.pim.compliance.application.services;

import com.retail.pim.catalog.application.facades.CatalogRecallFacade;
import com.retail.pim.compliance.application.ports.KafkaQosPort;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates emergency Class 1 safety recalls:
 * 1. Executes immediate in-process variant quarantine hold via CatalogRecallFacade.
 * 2. Emits ProductLotRecallInitiatedEvent directly to the Kafka QoS Express Lane with zero database wait.
 */
@Service
@Transactional
public class EmergencyRecallService implements EmergencyRecallUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRecallService.class);

    private final CatalogRecallFacade catalogRecallFacade;
    private final KafkaQosPort kafkaQosPort;

    public EmergencyRecallService(CatalogRecallFacade catalogRecallFacade,
                                  KafkaQosPort kafkaQosPort) {
        this.catalogRecallFacade = catalogRecallFacade;
        this.kafkaQosPort = kafkaQosPort;
    }

    @Override
    public ProductLotRecallInitiatedEvent initiateRecall(String sku, String lotNumber, String severity, String reason) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU is mandatory for recall execution");
        }
        if (lotNumber == null || lotNumber.isBlank()) {
            throw new IllegalArgumentException("Lot number is mandatory for recall execution");
        }

        String finalSeverity = (severity != null && !severity.isBlank()) ? severity : "CLASS_1_CRITICAL_SAFETY";
        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Emergency Class 1 Safety Recall";

        log.warn("Declaring emergency recall: SKU [{}], Lot [{}], Severity [{}]", sku, lotNumber, finalSeverity);

        // 1. Immediate in-process quarantine transition
        catalogRecallFacade.quarantineVariant(sku, lotNumber, finalReason);

        // 2. Dispatch to Kafka QoS Express Lane with zero database wait
        ProductLotRecallInitiatedEvent event = new ProductLotRecallInitiatedEvent(
                sku,
                lotNumber,
                finalSeverity,
                finalReason
        );
        kafkaQosPort.publishRecallQosEvent(event);

        log.info("Emergency recall initiated successfully for event [{}]", event.eventId());
        return event;
    }
}
