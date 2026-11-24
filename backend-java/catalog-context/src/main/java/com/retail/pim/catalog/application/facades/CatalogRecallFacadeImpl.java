package com.retail.pim.catalog.application.facades;

import com.retail.pim.catalog.domain.enums.RestrictionReason;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogRecallFacadeImpl implements CatalogRecallFacade {

    private static final Logger log = LoggerFactory.getLogger(CatalogRecallFacadeImpl.class);

    private final ProductRepository productRepository;

    public CatalogRecallFacadeImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void quarantineVariant(String sku, String lotNumber, String reason) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU is required for recall quarantine");
        }

        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily not found for SKU: " + sku));

        Variant variant = family.getVariantBySku(sku);

        log.warn("Initiating emergency recall quarantine for SKU [{}] Lot [{}]. Current state: [{}]",
                sku, lotNumber, variant.getLifecycleState());

        // 1. Transition variant to SKU_RECALLED_LOT_HOLD
        variant.transitionTo(VariantLifecycleState.SKU_RECALLED_LOT_HOLD);

        // 2. Add suspension reason and safety mask
        String suspensionReason = String.format("RECALL_HOLD:LOT_%s:%s",
                lotNumber != null ? lotNumber : "ALL",
                reason != null ? reason : "Class 1 Safety Recall");
        variant.addSuspensionReason(suspensionReason);
        variant.applyRegionalMask("GLOBAL", RestrictionReason.SAFETY_RESTRICTION);

        // 3. Persist product family
        productRepository.save(family);

        log.warn("Successfully quarantined SKU [{}] to SKU_RECALLED_LOT_HOLD (active={})", sku, variant.isActive());
    }
}
