package com.retail.pim.catalog.application.facades;

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
 * Implementation of CatalogQuarantineFacade executing operational lot-quarantine exit bypasses.
 */
@Service
public class CatalogQuarantineFacadeImpl implements CatalogQuarantineFacade {

    private static final Logger log = LoggerFactory.getLogger(CatalogQuarantineFacadeImpl.class);

    private final ProductRepository productRepository;

    public CatalogQuarantineFacadeImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Variant certifyLotQuarantine(String sku, String lotNumber) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new DomainException("Product family not found for variant SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);
        if (variant == null) {
            throw new DomainException("Variant not found for SKU: " + sku);
        }

        if (variant.getLifecycleState() != VariantLifecycleState.SKU_RECALLED_LOT_HOLD) {
            throw new DomainException(String.format(
                    "Cannot certify quarantine bypass for SKU '%s': Current state is '%s', must be SKU_RECALLED_LOT_HOLD",
                    sku, variant.getLifecycleState()));
        }

        // Direct operational exit bypass back to SKU_ACTIVE
        variant.transitionTo(VariantLifecycleState.SKU_ACTIVE);
        if (lotNumber != null && !lotNumber.isBlank()) {
            variant.removeSuspensionReason("SAFETY_RECALL_" + lotNumber.trim());
        }
        variant.removeSuspensionReason("CLASS_1_SAFETY_RECALL");

        // Synchronize parent family state
        family.evaluateVariantCount(family.countActiveVariants());
        productRepository.save(family);

        log.info("WMS Quarantine Certified: Variant [{}] restored from SKU_RECALLED_LOT_HOLD to SKU_ACTIVE for lot [{}]",
                sku, lotNumber);

        return variant;
    }
}
