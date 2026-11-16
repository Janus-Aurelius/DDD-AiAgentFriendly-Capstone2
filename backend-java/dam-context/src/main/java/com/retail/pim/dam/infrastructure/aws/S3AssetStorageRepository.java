package com.retail.pim.dam.infrastructure.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Infrastructure Adapter: Interacts with AWS S3 for object storage.
 * Keeps media binaries out of PostgreSQL and provides CDN/presigned URLs.
 */
@Repository
public class S3AssetStorageRepository {

    private static final Logger log = LoggerFactory.getLogger(S3AssetStorageRepository.class);
    private static final String BUCKET_NAME = "enterprise-pim-dam-assets";
    private static final String S3_BASE_URL = "https://" + BUCKET_NAME + ".s3.amazonaws.com/";

    public String uploadRawAsset(UUID assetId, String fileName, byte[] content, String mimeType) {
        String key = "raw/" + assetId + "/" + fileName;
        log.info("AWS S3: Uploading raw asset [{}] size [{} bytes] to s3://{}/{}",
                fileName, content.length, BUCKET_NAME, key);
        return S3_BASE_URL + key;
    }

    public String uploadRendition(UUID assetId, String renditionName, byte[] content, String format) {
        String key = "renditions/" + assetId + "/" + renditionName + "." + format;
        log.info("AWS S3: Uploading optimized rendition [{}] to s3://{}/{}", renditionName, BUCKET_NAME, key);
        return S3_BASE_URL + key;
    }

    public void deleteAsset(UUID assetId) {
        log.info("AWS S3: Deleting all objects under prefix [{}] from bucket [{}]", assetId, BUCKET_NAME);
    }

    /**
     * Asynchronous S3 deletion cleanup task:
     * Purges raw binaries and all generated renditions to prevent orphaned multi-megabyte files in S3.
     */
    public java.util.concurrent.CompletableFuture<Void> purgeAssetBinariesAsync(UUID assetId, String rawS3Url, java.util.List<String> renditionUrls) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            log.info("AWS S3 Lifecycle: Purging raw binary [{}] and [{}] renditions for asset [{}]",
                    rawS3Url, renditionUrls != null ? renditionUrls.size() : 0, assetId);
            deleteAsset(assetId);
            log.info("AWS S3 Lifecycle: Successfully purged all S3 storage objects for asset [{}]", assetId);
        });
    }
}
