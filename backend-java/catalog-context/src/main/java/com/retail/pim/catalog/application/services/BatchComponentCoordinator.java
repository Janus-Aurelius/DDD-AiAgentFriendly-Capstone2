package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.ports.BundleMaskNotificationPort;
import com.retail.pim.catalog.domain.repository.BundleComponentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Batch Coordinator for high-throughput bundle component masking & database fan-out cascades:
 * 1. Executes instant (<2ms) component blacklist notification via BundleMaskNotificationPort.
 * 2. Fetches parent bundle variant IDs from catalog.bundle_component.
 * 3. Partitions bundles into bounded chunks of 250 with deterministic ascending lock ordering (ORDER BY id).
 * 4. Updates variants and companion parent families (auto-suspending families with 0 active variants).
 * 5. Notifies read-model projectors of affected bundles.
 */
@Service
@Transactional
public class BatchComponentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(BatchComponentCoordinator.class);
    public static final int CHUNK_SIZE = 250;

    private final BundleComponentRepository bundleComponentRepository;
    private final NamedParameterJdbcOperations jdbcOperations;
    private final BundleMaskNotificationPort notificationPort;

    public BatchComponentCoordinator(BundleComponentRepository bundleComponentRepository,
                                     @Autowired(required = false) NamedParameterJdbcOperations jdbcOperations,
                                     @Autowired(required = false) BundleMaskNotificationPort notificationPort) {
        this.bundleComponentRepository = bundleComponentRepository;
        this.jdbcOperations = jdbcOperations;
        this.notificationPort = notificationPort;
    }

    public record BatchCascadeResult(
            String componentSku,
            int totalBundlesAffected,
            int chunksProcessed
    ) {}

    public BatchCascadeResult maskComponentInBundles(String componentSku) {
        if (componentSku == null || componentSku.isBlank()) {
            throw new IllegalArgumentException("Component SKU cannot be null or blank");
        }

        log.warn("Starting high-throughput bundle masking cascade for component SKU [{}]", componentSku);

        // 1. Instant <2ms Storefront Availability protection via Redis blacklist
        if (notificationPort != null) {
            notificationPort.notifyComponentBlacklisted(componentSku);
        }

        // 2. Identify all parent bundle variant IDs
        List<UUID> parentBundleIds = bundleComponentRepository.findBundleVariantIdsByComponentSku(componentSku);
        if (parentBundleIds.isEmpty()) {
            log.info("No parent bundles found containing component SKU [{}]", componentSku);
            return new BatchCascadeResult(componentSku, 0, 0);
        }

        log.info("Found [{}] parent bundles containing component SKU [{}]. Beginning chunked cascade (batch size = {})",
                parentBundleIds.size(), componentSku, CHUNK_SIZE);

        // 3. Partition into chunks of CHUNK_SIZE (250)
        List<List<UUID>> chunks = partition(parentBundleIds, CHUNK_SIZE);
        int chunksProcessed = 0;

        for (List<UUID> chunk : chunks) {
            // 4. Deterministic Lock Ordering: Sort chunk IDs in ascending order prior to SQL execution
            List<UUID> sortedChunk = new ArrayList<>(chunk);
            Collections.sort(sortedChunk);

            // 5. Execute batch SQL on product_variant
            updateVariantsChunk(sortedChunk, componentSku);

            // 6. Execute companion family synchronization SQL
            syncParentFamiliesChunk(sortedChunk);

            // 7. Notify read-model projector for edge index synchronization
            if (notificationPort != null) {
                notificationPort.notifyBundlesMasked(sortedChunk);
            }

            chunksProcessed++;
            log.debug("Processed bundle cascade chunk [{}/{}] with [{}] variants",
                    chunksProcessed, chunks.size(), sortedChunk.size());
        }

        log.warn("Completed bundle cascade for component [{}]: [{}] bundles across [{}] chunks updated",
                componentSku, parentBundleIds.size(), chunksProcessed);

        return new BatchCascadeResult(componentSku, parentBundleIds.size(), chunksProcessed);
    }

    private void updateVariantsChunk(List<UUID> sortedChunk, String componentSku) {
        if (jdbcOperations == null) {
            return;
        }

        String sql = """
            UPDATE catalog.product_variant
            SET active_suspension_reasons = active_suspension_reasons || '{"COMPONENT_DEPENDENCY": true}'::jsonb,
                suspended_components      = suspended_components || jsonb_build_object(:componentSku, true),
                version                   = version + 1
            WHERE id IN (:ids)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("componentSku", componentSku)
                .addValue("ids", sortedChunk);

        jdbcOperations.update(sql, params);
    }

    private void syncParentFamiliesChunk(List<UUID> sortedChunk) {
        if (jdbcOperations == null) {
            return;
        }

        String sql = """
            UPDATE catalog.product_family pf
            SET active_suspension_reasons = pf.active_suspension_reasons || '{"NO_ACTIVE_VARIANTS": true}'::jsonb,
                lifecycle_state           = 'FAMILY_SUSPENDED',
                version                   = pf.version + 1
            WHERE pf.id IN (
                SELECT DISTINCT pv.family_id 
                FROM catalog.product_variant pv 
                WHERE pv.id IN (:ids)
            )
            AND (
                SELECT COUNT(*) 
                FROM catalog.product_variant v 
                WHERE v.family_id = pf.id 
                  AND v.lifecycle_state = 'SKU_ACTIVE'
                  AND (v.active_suspension_reasons = '{}'::jsonb OR v.active_suspension_reasons IS NULL)
            ) = 0
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", sortedChunk);

        jdbcOperations.update(sql, params);
    }

    static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return partitions;
    }
}
