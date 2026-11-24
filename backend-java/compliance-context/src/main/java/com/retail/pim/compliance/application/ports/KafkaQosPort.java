package com.retail.pim.compliance.application.ports;

import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;

/**
 * Outgoing port for high-priority emergency event dispatch via Kafka QoS Express Lane.
 */
public interface KafkaQosPort {

    void publishRecallQosEvent(ProductLotRecallInitiatedEvent event);
}
