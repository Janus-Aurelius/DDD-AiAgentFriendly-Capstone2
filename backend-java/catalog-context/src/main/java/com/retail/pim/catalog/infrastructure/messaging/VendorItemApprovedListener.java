package com.retail.pim.catalog.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * The Supplier Context acts as the quarantine and translation boundary. When it finishes
 * AI mapping and validation, it publishes `VendorItemApprovedForCore`. This listener picks it up,
 * translates the external event to the internal Ubiquitous Language (`CreateDraftProductCommand`),
 * and triggers aggregate instantiation in DRAFT state.
 */
@Component
public class VendorItemApprovedListener {

    private static final Logger log = LoggerFactory.getLogger(VendorItemApprovedListener.class);

    private final ProductLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public VendorItemApprovedListener(ProductLifecycleService lifecycleService, ObjectMapper objectMapper) {
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
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
        log.info("Kafka: Received raw VendorItemApprovedForCore payload");
        try {
            VendorItemApprovedForCore event = objectMapper.readValue(rawMessage, VendorItemApprovedForCore.class);
            log.info("Processing VendorItemApprovedForCore: vendorItem=[{}], code=[{}]",
                    event.vendorItemId(), event.cleanCode());

            // 1. Translate external event schema to internal domain command
            CreateDraftProductCommand command = new CreateDraftProductCommand(
                    event.cleanCode(),
                    event.cleanTitle(),
                    event.cleanDescription(),
                    event.mappedCategoryId(),
                    mapAttributes(event.attributes()),
                    event.vendorItemId()
            );

            // 2. Delegate to application orchestration service
            lifecycleService.createDraftProduct(command);

            log.info("Successfully ingested vendor item [{}] into Core Catalog as DRAFT product [{}]",
                    event.vendorItemId(), event.cleanCode());

        } catch (DomainException de) {
            // Business rule violations (e.g. duplicate code already exists)
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
