package com.retail.pim.localization.application.services;

import com.retail.pim.localization.application.commands.ApplyRegionalOverrideCommand;
import com.retail.pim.localization.domain.model.RegionRule;
import com.retail.pim.localization.domain.model.RegionalCatalogItem;
import com.retail.pim.localization.domain.repository.RegionalCatalogRepository;
import com.retail.pim.localization.domain.service.RegionRuleRegistry;
import com.retail.pim.localization.domain.service.UomConverter;
import com.retail.pim.localization.domain.valueobjects.TranslationBlock;
import com.retail.pim.localization.infrastructure.persistence.LocalizationOutboxService;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@Transactional
public class RegionalCatalogService {

    private static final Logger log = LoggerFactory.getLogger(RegionalCatalogService.class);

    private final RegionalCatalogRepository repository;
    private final LocalizationOutboxService outboxService;
    private final UomConverter uomConverter;
    private final RegionRuleRegistry ruleRegistry;

    public RegionalCatalogService(RegionalCatalogRepository repository,
                                  LocalizationOutboxService outboxService,
                                  UomConverter uomConverter,
                                  RegionRuleRegistry ruleRegistry) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.uomConverter = uomConverter;
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * The Fan-out: Instantiates RegionalCatalogItems for all company regions
     * when a global ProductPublished event is received from the Core Catalog.
     * Hardened with Non-Destructive Delta Sync and dynamic RegionRuleRegistry.
     */
    public void fanOutRegionalItems(UUID globalProductId, String productCode) {
        Collection<String> operationalRegions = ruleRegistry.getOperationalRegionCodes();
        log.info("Localization Fan-out: Initializing regional catalog items for [{}] across dynamic regions: {}",
                productCode, operationalRegions);

        for (String region : operationalRegions) {
            var existing = repository.findByGlobalProductAndRegion(globalProductId, region);
            if (existing.isEmpty()) {
                RegionalCatalogItem item = new RegionalCatalogItem(
                        UUID.randomUUID(),
                        globalProductId,
                        productCode,
                        region
                );
                repository.save(item);
                log.info("Initialized RegionalCatalogItem [{} - {}] in state [{}]",
                        productCode, region, item.getStatus());
            } else {
                log.info("Non-Destructive Delta Sync: RegionalCatalogItem [{} - {}] already exists. Preserving existing translations and overrides.",
                        productCode, region);
            }
        }
    }

    /**
     * Applies regional attribute overrides (e.g. converting 50lbs to 22.6kg).
     * Hardened with UomConverter for deterministic automated unit conversion.
     */
    public RegionalCatalogItem applyOverride(ApplyRegionalOverrideCommand command) {
        RegionalCatalogItem item = repository.findByGlobalProductAndRegion(command.globalProductId(), command.regionCode())
                .orElseThrow(() -> new ResourceNotFoundException("RegionalCatalogItem", command.regionCode()));

        String normalizedValue = uomConverter.convertForRegion(
                command.attributeKey(),
                command.regionalValue(),
                command.regionCode()
        );

        item.applyOverride(command.attributeKey(), command.globalDefaultValue(), normalizedValue, command.reason());
        RegionalCatalogItem saved = repository.save(item);
        stageOutboxEvents(saved);
        return saved;
    }

    /**
     * The Resolution: Adds verified localized translation block (e.g. French safety warnings).
     * Validates compliance against dynamic RegionRule from the registry.
     */
    public RegionalCatalogItem addTranslation(UUID globalProductId, String regionCode, TranslationBlock block) {
        RegionalCatalogItem item = repository.findByGlobalProductAndRegion(globalProductId, regionCode)
                .orElseThrow(() -> new ResourceNotFoundException("RegionalCatalogItem", regionCode));

        item.addTranslationBlock(block);

        RegionRule rule = ruleRegistry.getRule(regionCode).orElse(null);
        item.evaluateReadiness(rule);

        RegionalCatalogItem saved = repository.save(item);
        stageOutboxEvents(saved);
        return saved;
    }

    private void stageOutboxEvents(RegionalCatalogItem item) {
        for (DomainEvent event : item.getDomainEvents()) {
            outboxService.saveEvent("RegionalCatalogItem", item.getId(), event);
        }
        item.clearDomainEvents();
    }
}
