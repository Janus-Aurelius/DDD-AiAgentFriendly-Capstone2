package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing variant lifecycle state transitions and enforcing
 * the "Last Variant Standing" aggregate rule on the parent ProductFamily (CUJ 6).
 */
@Service
public class VariantStateManagementService {

    private static final Logger log = LoggerFactory.getLogger(VariantStateManagementService.class);

    private final ProductRepository productRepository;

    public VariantStateManagementService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Executes a state transition on a variant and automatically checks if parent family
     * active variant count has dropped to 0 or recovered.
     */
    @Transactional
    public Variant transitionVariantState(String sku, VariantLifecycleState targetState) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        VariantLifecycleState previousState = variant.getLifecycleState();
        variant.transitionTo(targetState);
        log.info("Variant [{}] transitioned: {} -> {}", sku, previousState, targetState);

        // Evaluate "Last Variant Standing" rule on parent family
        int activeCount = family.countActiveVariants();
        family.evaluateVariantCount(activeCount);
        log.info("Parent family [{}] evaluated with active variant count [{}], lifecycleState [{}], holds [{}]",
                family.getCode(), activeCount, family.getLifecycleState(), family.getActiveSuspensionReasons());

        productRepository.save(family);
        return variant;
    }

    /**
     * Suspends a variant for an explicit reason and synchronizes parent family state.
     */
    @Transactional
    public Variant suspendVariant(String sku, String reason) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        variant.addSuspensionReason(reason);
        return transitionVariantState(sku, VariantLifecycleState.SKU_SUSPENDED);
    }

    /**
     * Clears a suspension reason from a variant. If no more suspension reasons exist,
     * restores variant to SKU_ACTIVE and updates parent family state.
     */
    @Transactional
    public Variant restoreVariant(String sku, String reason) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        variant.removeSuspensionReason(reason);
        if (variant.getActiveSuspensionReasons().isEmpty()) {
            return transitionVariantState(sku, VariantLifecycleState.SKU_ACTIVE);
        }

        productRepository.save(family);
        return variant;
    }
}
