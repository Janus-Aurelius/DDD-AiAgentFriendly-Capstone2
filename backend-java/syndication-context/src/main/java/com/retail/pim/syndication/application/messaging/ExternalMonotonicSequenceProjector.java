package com.retail.pim.syndication.application.messaging;

import com.retail.pim.syndication.application.projections.ProductReadModelProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ingests external asynchronous Kafka streams (pricing, inventory) and enforces
 * strictly Monotonic Sequence Ordering (CUJ 8).
 *
 * Prevents race conditions and out-of-order message drops:
 * If incoming.version > lastProcessedVersion: Applies update and advances lastProcessedVersion.
 * If incoming.version <= lastProcessedVersion: Discards event as stale without mutating read model state.
 */
@Service
public class ExternalMonotonicSequenceProjector {

    private static final Logger log = LoggerFactory.getLogger(ExternalMonotonicSequenceProjector.class);

    public static final String STREAM_PRICING = "ERP_PRICING";
    public static final String STREAM_INVENTORY = "OMS_INVENTORY";

    private final ProductReadModelProjector projector;

    // Track monotonic sequence versions: key = "streamType:region:sku"
    private final Map<String, Long> lastProcessedVersions = new ConcurrentHashMap<>();

    public ExternalMonotonicSequenceProjector(ProductReadModelProjector projector) {
        this.projector = projector;
    }

    /**
     * Ingests ERP Pricing updates with monotonic sequence validation.
     */
    public boolean processPricingUpdate(String sku, String regionCode, BigDecimal price, String currency, long version) {
        String region = regionCode != null ? regionCode : "US";
        String streamKey = STREAM_PRICING + ":" + region + ":" + sku;

        Long currentVersion = lastProcessedVersions.getOrDefault(streamKey, -1L);
        if (version <= currentVersion) {
            log.warn("Monotonic Guard: Discarded stale ERP pricing event for SKU [{}] in region [{}] (incoming version {} <= current {})",
                    sku, region, version, currentVersion);
            return false;
        }

        // Apply update and advance monotonic sequence
        projector.projectErpPrice(sku, region, price, currency, version);
        lastProcessedVersions.put(streamKey, version);
        log.info("Monotonic Guard: Applied ERP pricing v{} for SKU [{}] (advanced from v{})",
                version, sku, currentVersion);
        return true;
    }

    /**
     * Ingests OMS Inventory updates with monotonic sequence validation.
     */
    public boolean processInventoryUpdate(String sku, String regionCode, int availableStock, long version) {
        String region = regionCode != null ? regionCode : "US";
        String streamKey = STREAM_INVENTORY + ":" + region + ":" + sku;

        Long currentVersion = lastProcessedVersions.getOrDefault(streamKey, -1L);
        if (version <= currentVersion) {
            log.warn("Monotonic Guard: Discarded stale OMS inventory event for SKU [{}] in region [{}] (incoming version {} <= current {})",
                    sku, region, version, currentVersion);
            return false;
        }

        // Apply update and advance monotonic sequence
        projector.projectOmsStock(sku, region, availableStock, version);
        lastProcessedVersions.put(streamKey, version);
        log.info("Monotonic Guard: Applied OMS inventory v{} for SKU [{}] (advanced from v{})",
                version, sku, currentVersion);
        return true;
    }

    public long getLastProcessedVersion(String streamType, String regionCode, String sku) {
        String region = regionCode != null ? regionCode : "US";
        String streamKey = streamType + ":" + region + ":" + sku;
        return lastProcessedVersions.getOrDefault(streamKey, -1L);
    }
}
