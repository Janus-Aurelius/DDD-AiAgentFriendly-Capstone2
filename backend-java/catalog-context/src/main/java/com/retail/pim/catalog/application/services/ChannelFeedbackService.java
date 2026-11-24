package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.facades.CatalogChannelFeedbackFacade.ChannelFeedbackResult;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service enforcing omnichannel feed resilience and version-pinned anti-thrashing validation.
 */
@Service
@Transactional
public class ChannelFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ChannelFeedbackService.class);

    private final ProductRepository productRepository;

    public ChannelFeedbackService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ChannelFeedbackResult processRejection(String sku, String channel, long evaluatedVersion, String errorMessage) {
        if (sku == null || sku.isBlank() || channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("SKU and channel must not be blank");
        }

        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily not found for SKU: " + sku));
        Variant variant = family.getVariantBySku(sku);

        // Version-Pinning Anti-Thrashing Guard:
        // Discard rejection if feed evaluated an older version than current publishedVersion
        if (evaluatedVersion < variant.getPublishedVersion()) {
            log.warn("DISCARDED STALE REJECTION: Channel [{}] reported rejection for SKU [{}] evaluated at version [{}] " +
                     "which is older than current published version [{}]. Error ignored to prevent feed thrashing: {}",
                    channel, sku, evaluatedVersion, variant.getPublishedVersion(), errorMessage);
            return ChannelFeedbackResult.DISCARDED_STALE;
        }

        log.warn("APPLYING CHANNEL REJECTION: Channel [{}] rejected current version [{}] of SKU [{}]: {}",
                channel, evaluatedVersion, sku, errorMessage);

        // 1. Mark channel restricted
        variant.applyChannelStatus(channel, "RESTRICTED");

        // 2. Add diagnostic suspension reason
        String reason = String.format("CHANNEL_REJECTION:%s:v%d:%s", channel, evaluatedVersion,
                errorMessage != null ? errorMessage : "Feed rejection");
        variant.addSuspensionReason(reason);

        // 3. Persist product family
        productRepository.save(family);

        return ChannelFeedbackResult.APPLIED;
    }

    public ChannelFeedbackResult processSuccess(String sku, String channel, long evaluatedVersion) {
        if (sku == null || sku.isBlank() || channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("SKU and channel must not be blank");
        }

        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily not found for SKU: " + sku));
        Variant variant = family.getVariantBySku(sku);

        if (evaluatedVersion < variant.getPublishedVersion()) {
            log.info("DISCARDED STALE SUCCESS: Channel [{}] reported success for older version [{}] of SKU [{}] (current={})",
                    channel, evaluatedVersion, sku, variant.getPublishedVersion());
            return ChannelFeedbackResult.DISCARDED_STALE;
        }

        log.info("APPLYING CHANNEL SUCCESS: Channel [{}] published version [{}] of SKU [{}] successfully",
                channel, evaluatedVersion, sku);

        variant.applyChannelStatus(channel, "ACTIVE");
        productRepository.save(family);

        return ChannelFeedbackResult.APPLIED;
    }

    public Map<String, String> getChannelStatuses(String sku) {
        ProductFamily family = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("ProductFamily not found for SKU: " + sku));
        Variant variant = family.getVariantBySku(sku);
        return variant.getChannelStatusMap();
    }
}
