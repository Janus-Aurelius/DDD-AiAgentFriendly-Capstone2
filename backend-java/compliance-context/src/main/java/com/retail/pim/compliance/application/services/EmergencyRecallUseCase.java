package com.retail.pim.compliance.application.services;

import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;

/**
 * Use case interface for emergency safety recall operations.
 */
public interface EmergencyRecallUseCase {

    ProductLotRecallInitiatedEvent initiateRecall(String sku, String lotNumber, String severity, String reason);
}
