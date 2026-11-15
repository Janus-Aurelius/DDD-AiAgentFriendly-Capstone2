package com.retail.pim.dam.application.services;

import com.retail.pim.dam.domain.model.DigitalAsset;
import com.retail.pim.dam.domain.model.Rendition;
import com.retail.pim.dam.domain.repository.DigitalAssetRepository;
import com.retail.pim.dam.domain.valueobjects.AssetMetadata;
import com.retail.pim.dam.infrastructure.aws.S3AssetStorageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Background Processing Worker: Handles asynchronous image compression,
 * EXIF metadata extraction, and multi-channel rendition generation.
 */
@Service
@Transactional
public class RenditionGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(RenditionGenerationWorker.class);

    private final DigitalAssetRepository assetRepository;
    private final S3AssetStorageRepository s3Repository;

    public RenditionGenerationWorker(DigitalAssetRepository assetRepository, S3AssetStorageRepository s3Repository) {
        this.assetRepository = assetRepository;
        this.s3Repository = s3Repository;
    }

    /**
     * Simulates image processing pipeline:
     * 1. Extracts EXIF metadata
     * 2. Compresses raw image into channel-specific renditions
     * 3. Uploads renditions to S3
     * 4. Updates DigitalAsset aggregate root
     */
    public void processUploadedAsset(UUID assetId, byte[] rawBytes, String originalFileName) {
        DigitalAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        log.info("RenditionWorker: Starting asset processing for [{}] ({} bytes)", originalFileName, rawBytes.length);

        // 1. Extract EXIF & resolution metadata
        AssetMetadata metadata = new AssetMetadata(
                3840,
                2160,
                "Adobe RGB (1998)",
                "Sony Alpha 7R V",
                "image/tiff",
                rawBytes.length
        );
        asset.setMetadata(metadata);

        // 2. Generate 4K Original Rendition
        String url4k = s3Repository.uploadRendition(assetId, "4K_Original", rawBytes, "tiff");
        asset.addRendition(new Rendition(
                UUID.randomUUID(),
                Rendition.RenditionType.ORIGINAL_4K,
                url4k,
                "tiff",
                3840,
                2160,
                rawBytes.length
        ));

        // 3. Generate Mobile Web Optimized Rendition (WebP)
        long webpSize = (long) (rawBytes.length * 0.15); // simulated 85% compression
        String urlWebp = s3Repository.uploadRendition(assetId, "Mobile_Web_Optimized", new byte[0], "webp");
        asset.addRendition(new Rendition(
                UUID.randomUUID(),
                Rendition.RenditionType.MOBILE_WEB_OPTIMIZED,
                urlWebp,
                "webp",
                1280,
                720,
                webpSize
        ));

        // 4. Generate Thumbnail (100x100 JPEG)
        long thumbSize = 8192; // 8KB
        String urlThumb = s3Repository.uploadRendition(assetId, "Thumbnail_100x100", new byte[0], "jpeg");
        asset.addRendition(new Rendition(
                UUID.randomUUID(),
                Rendition.RenditionType.THUMBNAIL_100X100,
                urlThumb,
                "jpeg",
                100,
                100,
                thumbSize
        ));

        assetRepository.save(asset);
        log.info("RenditionWorker: Completed 3 renditions for asset [{}]", assetId);
    }
}
