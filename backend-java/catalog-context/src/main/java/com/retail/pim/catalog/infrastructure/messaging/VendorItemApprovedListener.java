package com.retail.pim.catalog.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.commands.CreateDraftProductCommand;
import com.retail.pim.catalog.application.services.ProductLifecycleService;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.exceptions.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound Messaging Adapter: Listens to the Enterprise Event Bus for approved supplier items.
 *
 * Hardened with:
 * 1. Idempotency Guard (catalog.consumed_messages) preventing duplicate execution on re-delivery.
 * 2. Supplier Re-import Delta Merging (updates existing products rather than failing if code already exists).
 */
@Component
public class VendorItemApprovedListener {

    private static final Logger log = LoggerFactory.getLogger(VendorItemApprovedListener.class);
    private static final String CONSUMER_GROUP = "pim-catalog-core-consumer";

    private final ProductLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;
    private final IdempotentConsumerGuard idempotentGuard;

    public VendorItemApprovedListener(ProductLifecycleService lifecycleService,
                                      ObjectMapper objectMapper,
                                      IdempotentConsumerGuard idempotentGuard) {
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
        this.idempotentGuard = idempotentGuard;
    }

    /**
     * External Event Contract received from Supplier Context (Context 1 Quarantine Layer)
     */
    public record VendorItemApprovedForCore(
            UUID eventId,
            String vendorId,
            String vendorItemId,
            String cleanCode,
            String cleanTitle,
            String cleanDescription,
            UUID mappedCategoryId,
            Map<String, Object> attributes
    ) {}

    @KafkaListener(
            topics = "${kafka.topics.vendor-item-approved:supplier.vendor-item.approved}",
            groupId = "${spring.kafka.consumer.group-id:pim-catalog-core-consumer}"
    )
    public void onVendorItemApproved(String rawMessage) {
        try {
            VendorItemApprovedForCore event = objectMapper.readValue(rawMessage, VendorItemApprovedForCore.class);
            log.info("Processing VendorItemApprovedForCore: vendorItem=[{}], code=[{}], eventId=[{}]",
                    event.vendorItemId(), event.cleanCode(), event.eventId());

            // 1. Idempotency Check
            if (event.eventId() != null && !idempotentGuard.tryConsume(event.eventId(), CONSUMER_GROUP)) {
                log.info("Idempotent Consumer: Skipping already processed event [{}]", event.eventId());
                return;
            }

            Map<String, AttributeValue> domainAttrs = mapAttributes(event.attributes());

            // 2. Translate external event schema to internal domain command
            CreateDraftProductCommand command = new CreateDraftProductCommand(
                    event.cleanCode(),
                    event.cleanTitle(),
                    event.cleanDescription(),
                    event.mappedCategoryId(),
                    domainAttrs,
                    event.vendorItemId()
            );

            // 3. Delegate to application service with delta-merge fallback for re-imports
            try {
                lifecycleService.createDraftProduct(command);
                log.info("Successfully ingested vendor item [{}] into Core Catalog as DRAFT product [{}]",
                        event.vendorItemId(), event.cleanCode());
            } catch (DomainException de) {
                if (de.getMessage() != null && de.getMessage().contains("already exists")) {
                    log.info("Vendor item [{}] with code [{}] already exists. Executing non-destructive delta merge.",
                            event.vendorItemId(), event.cleanCode());
                    lifecycleService.mergeSupplierUpdate(event.cleanCode(), domainAttrs, event.cleanDescription());
                } else {
                    throw de;
                }
            }

        } catch (DomainException de) {
            log.warn("Business rule violation processing VendorItemApprovedForCore event: {}", de.getMessage());
        } catch (Exception e) {
            log.error("Failed to process VendorItemApprovedForCore message: {}", rawMessage, e);
            throw new RuntimeException("Error processing supplier approval event", e);
        }
    }

    private Map<String, AttributeValue> mapAttributes(Map<String, Object> rawAttributes) {
        Map<String, AttributeValue> result = new HashMap<>();
        if (rawAttributes == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : rawAttributes.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Number n) {
                result.put(entry.getKey(), AttributeValue.ofNumber(n, null));
            } else if (val instanceof Boolean b) {
                result.put(entry.getKey(), AttributeValue.ofBoolean(b));
            } else if (val != null) {
                result.put(entry.getKey(), AttributeValue.ofString(val.toString()));
            }
        }
        return result;
    }
}
