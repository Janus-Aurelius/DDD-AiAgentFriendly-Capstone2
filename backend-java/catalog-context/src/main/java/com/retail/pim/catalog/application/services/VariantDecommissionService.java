package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.events.ProductTombstonedEvent;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing end-of-life decommissioning and tombstoning of product variants (CUJ 8).
 * Transitions variant to SKU_DISCONTINUED or SKU_ARCHIVED, dispatches ProductTombstonedEvent
 * to purge edge caches, and evaluates parent family "Last Variant Standing" state.
 */
@Service
public class VariantDecommissionService {

    private static final Logger log = LoggerFactory.getLogger(VariantDecommissionService.class);

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VariantDecommissionService(ProductRepository productRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Decommissions an active or recalled variant to SKU_DISCONTINUED and emits ProductTombstonedEvent.
     */
    @Transactional
    public Variant decommissionVariant(String sku, String reason) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        variant.transitionTo(VariantLifecycleState.SKU_DISCONTINUED);
        log.info("Decommissioned variant [{}] to SKU_DISCONTINUED. Reason: [{}]", sku, reason);

        // Evaluate parent family "Last Variant Standing"
        family.evaluateVariantCount(family.countActiveVariants());
        productRepository.save(family);

        // Publish tombstone domain event
        ProductTombstonedEvent tombstoneEvent = new ProductTombstonedEvent(sku, family.getId(), reason);
        eventPublisher.publishEvent(tombstoneEvent);
        log.info("Emitted ProductTombstonedEvent for SKU [{}]", sku);

        return variant;
    }

    /**
     * Archives a discontinued variant after statutory warranty/retention period (e.g. 7 years).
     */
    @Transactional
    public Variant archiveVariant(String sku) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        variant.transitionTo(VariantLifecycleState.SKU_ARCHIVED);
        family.evaluateVariantCount(family.countActiveVariants());
        productRepository.save(family);
        log.info("Archived variant [{}] to SKU_ARCHIVED", sku);

        return variant;
    }
}
