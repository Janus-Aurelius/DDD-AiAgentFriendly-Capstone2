package com.retail.pim.localization.application.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.localization.application.commands.ApplyRegionalOverrideCommand;
import com.retail.pim.localization.domain.model.RegionalCatalogItem;
import com.retail.pim.localization.domain.repository.RegionalCatalogRepository;
import com.retail.pim.localization.domain.valueobjects.TranslationBlock;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RegionalCatalogService {

    private static final Logger log = LoggerFactory.getLogger(RegionalCatalogService.class);
    public static final String REGIONAL_TOPIC = "localization.regional.events";

    private final RegionalCatalogRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Supported global operational regions
    private static final List<String> OPERATIONAL_REGIONS = List.of("US", "CA", "MX");

    public RegionalCatalogService(RegionalCatalogRepository repository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * The Fan-out: Instantiates RegionalCatalogItems for all company regions
     * when a global ProductPublished event is received from the Core Catalog.
     */
    public void fanOutRegionalItems(UUID globalProductId, String productCode) {
        log.info("Localization Fan-out: Initializing regional catalog items for [{}] across regions: {}",
                productCode, OPERATIONAL_REGIONS);

        for (String region : OPERATIONAL_REGIONS) {
            if (repository.findByGlobalProductAndRegion(globalProductId, region).isEmpty()) {
                RegionalCatalogItem item = new RegionalCatalogItem(
                        UUID.randomUUID(),
                        globalProductId,
                        productCode,
                        region
                );
                repository.save(item);
                log.info("Initialized RegionalCatalogItem [{} - {}] in state [{}]",
                        productCode, region, item.getStatus());
            }
        }
    }

    /**
     * Applies regional attribute overrides (e.g. converting 50lbs to 22.6kg).
     */
    public RegionalCatalogItem applyOverride(ApplyRegionalOverrideCommand command) {
        RegionalCatalogItem item = repository.findByGlobalProductAndRegion(command.globalProductId(), command.regionCode())
                .orElseThrow(() -> new ResourceNotFoundException("RegionalCatalogItem", command.regionCode()));

        item.applyOverride(command.attributeKey(), command.globalDefaultValue(), command.regionalValue(), command.reason());
        RegionalCatalogItem saved = repository.save(item);
        dispatchEvents(saved);
        return saved;
    }

    /**
     * The Resolution: Adds verified localized translation block (e.g. French safety warnings).
     */
    public RegionalCatalogItem addTranslation(UUID globalProductId, String regionCode, TranslationBlock block) {
        RegionalCatalogItem item = repository.findByGlobalProductAndRegion(globalProductId, regionCode)
                .orElseThrow(() -> new ResourceNotFoundException("RegionalCatalogItem", regionCode));

        item.addTranslationBlock(block);
        RegionalCatalogItem saved = repository.save(item);
        dispatchEvents(saved);
        return saved;
    }

    private void dispatchEvents(RegionalCatalogItem item) {
        for (DomainEvent event : item.getDomainEvents()) {
            try {
                String json = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(REGIONAL_TOPIC, event.getEventId().toString(), json);
                log.info("Kafka Localization: Broadcast event [{}] for region [{}]", event.getEventType(), item.getRegionCode());
            } catch (JsonProcessingException e) {
                log.error("Failed to publish regional event", e);
            }
        }
        item.clearDomainEvents();
    }
}
