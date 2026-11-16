package com.retail.pim.dam.application.services;

import com.retail.pim.dam.application.commands.LinkAssetToProductCommand;
import com.retail.pim.dam.application.commands.UploadAssetCommand;
import com.retail.pim.dam.domain.model.DigitalAsset;
import com.retail.pim.dam.domain.repository.DigitalAssetRepository;
import com.retail.pim.dam.infrastructure.aws.S3AssetStorageRepository;
import com.retail.pim.dam.infrastructure.messaging.DamEventPublisher;
import com.retail.pim.shared.domain.DomainEvent;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AssetLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AssetLifecycleService.class);

    private final DigitalAssetRepository assetRepository;
    private final S3AssetStorageRepository s3Storage;
    private final RenditionGenerationWorker renditionWorker;
    private final DamEventPublisher eventPublisher;
    private final com.retail.pim.dam.infrastructure.persistence.DamOutboxService outboxService;

    public AssetLifecycleService(DigitalAssetRepository assetRepository,
                                 S3AssetStorageRepository s3Storage,
                                 RenditionGenerationWorker renditionWorker,
                                 DamEventPublisher eventPublisher,
                                 com.retail.pim.dam.infrastructure.persistence.DamOutboxService outboxService) {
        this.assetRepository = assetRepository;
        this.s3Storage = s3Storage;
        this.renditionWorker = renditionWorker;
        this.eventPublisher = eventPublisher;
        this.outboxService = outboxService;
    }

    /**
     * Ingestion: Creates DigitalAsset, uploads raw binary to S3, dispatches AssetUploaded event,
     * and triggers background rendition generation.
     */
    public DigitalAsset uploadAsset(UploadAssetCommand command) {
        UUID tempId = UUID.randomUUID();
        String rawS3Url = s3Storage.uploadRawAsset(tempId, command.fileName(), command.rawBytes(), command.mimeType());

        DigitalAsset asset = DigitalAsset.create(
                command.fileName(),
                command.title(),
                rawS3Url,
                command.rawBytes().length,
                command.mimeType(),
                command.license()
        );

        DigitalAsset saved = assetRepository.save(asset);

        // Stage domain events via Transactional Outbox
        for (DomainEvent event : asset.getDomainEvents()) {
            outboxService.saveEvent("DigitalAsset", asset.getId(), event);
        }
        asset.clearDomainEvents();

        // Trigger asynchronous serverless rendition generation
        renditionWorker.processUploadedAssetAsync(saved.getId(), command.rawBytes(), command.fileName());

        return saved;
    }

    /**
     * The Linking: Merchandising links an image from DAM to a Core Catalog Product.
     */
    public void linkAssetToProduct(LinkAssetToProductCommand command) {
        DigitalAsset asset = assetRepository.findById(command.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("DigitalAsset", command.assetId()));

        asset.linkToProduct(command.productId(), command.sku(), command.isPrimaryImage());
        assetRepository.save(asset);

        for (DomainEvent event : asset.getDomainEvents()) {
            outboxService.saveEvent("DigitalAsset", asset.getId(), event);
        }
        asset.clearDomainEvents();
    }

    /**
     * The Rule/Lock: Periodic DRM compliance scanner.
     * If an image's DRM license expires, it deactivates the asset and stages AssetLicenseExpired in the outbox.
     */
    @Scheduled(fixedRate = 60000) // Scan every minute in production
    public void evaluateDrmExpirations() {
        List<DigitalAsset> activeAssets = assetRepository.findActiveAssetsWithLicenses();
        for (DigitalAsset asset : activeAssets) {
            boolean expired = asset.checkDrmExpiration();
            if (expired) {
                assetRepository.save(asset);
                log.warn("DAM DRM Compliance: Asset [{}] has EXPIRED. Staging AssetLicenseExpired in outbox.", asset.getId());
                for (DomainEvent event : asset.getDomainEvents()) {
                    outboxService.saveEvent("DigitalAsset", asset.getId(), event);
                }
                asset.clearDomainEvents();
            }
        }
    }

    /**
     * 24-Hour Pre-Warning Scanner: Detects active licenses expiring within the next 24 hours.
     */
    @Scheduled(fixedRate = 300000) // Scan every 5 minutes
    public void evaluateDrmExpiringSoon() {
        List<DigitalAsset> activeAssets = assetRepository.findActiveAssetsWithLicenses();
        for (DigitalAsset asset : activeAssets) {
            boolean expiringSoon = asset.checkExpiringSoon(java.time.Duration.ofHours(24));
            if (expiringSoon) {
                log.warn("DAM DRM Pre-Warning: Asset [{}] expiring within 24h. Emitting pre-warning event.", asset.getId());
                for (DomainEvent event : asset.getDomainEvents()) {
                    outboxService.saveEvent("DigitalAsset", asset.getId(), event);
                }
                asset.clearDomainEvents();
            }
        }
    }
}
