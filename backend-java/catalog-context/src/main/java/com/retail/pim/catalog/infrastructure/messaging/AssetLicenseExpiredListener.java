package com.retail.pim.catalog.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-Domain Listener: Connects DAM context to Core Catalog.
 *
 * When an asset's DRMLicense expires, the DAM broadcasts `AssetLicenseExpired`.
 * This listener catches the event, automatically strips the asset ID from all linked variants,
 * and drops the product's status back to ENRICHED if it no longer satisfies the minimum image requirement.
 */
@Component
public class AssetLicenseExpiredListener {

    private static final Logger log = LoggerFactory.getLogger(AssetLicenseExpiredListener.class);

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public AssetLicenseExpiredListener(ProductRepository productRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.dam-events:dam.asset.events}",
            groupId = "${spring.kafka.consumer.group-id:pim-catalog-dam-consumer}"
    )
    @Transactional
    public void onAssetEventReceived(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String eventType = root.path("eventType").asText();

            if (!"AssetLicenseExpired".equalsIgnoreCase(eventType)) {
                return; // Ignore other DAM events
            }

            UUID assetId = UUID.fromString(root.path("assetId").asText());
            log.warn("DAM Alert: Received AssetLicenseExpired for asset [{}]", assetId);

            JsonNode affectedProductsNode = root.path("affectedProductIds");
            if (affectedProductsNode.isArray()) {
                for (JsonNode prodNode : affectedProductsNode) {
                    UUID productId = UUID.fromString(prodNode.asText());
                    Optional<ProductFamily> opt = productRepository.findById(productId);

                    if (opt.isPresent()) {
                        ProductFamily product = opt.get();
                        boolean stripped = product.removeAssetAndDemoteIfNonCompliant(assetId);

                        if (stripped) {
                            productRepository.save(product);
                            log.info("Asset [{}] stripped from Product [{}]. Current Product Status: [{}]",
                                    assetId, product.getCode(), product.getStatus());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process AssetLicenseExpired event message: {}", rawMessage, e);
        }
    }
}
