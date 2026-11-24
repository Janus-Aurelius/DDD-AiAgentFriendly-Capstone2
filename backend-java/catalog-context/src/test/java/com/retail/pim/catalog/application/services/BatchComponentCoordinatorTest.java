package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.application.ports.BundleMaskNotificationPort;
import com.retail.pim.catalog.domain.repository.BundleComponentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("BatchComponentCoordinator Chunking & Deterministic Lock Ordering Tests")
class BatchComponentCoordinatorTest {

    private BundleComponentRepository bundleComponentRepository;
    private NamedParameterJdbcOperations jdbcOperations;
    private BundleMaskNotificationPort notificationPort;
    private BatchComponentCoordinator coordinator;

    private final String componentSku = "SKU-CBL";

    @BeforeEach
    void setUp() {
        bundleComponentRepository = mock(BundleComponentRepository.class);
        jdbcOperations = mock(NamedParameterJdbcOperations.class);
        notificationPort = mock(BundleMaskNotificationPort.class);

        coordinator = new BatchComponentCoordinator(
                bundleComponentRepository,
                jdbcOperations,
                notificationPort
        );
    }

    @Test
    @DisplayName("Should partition large bundle list into chunks of 250 with deterministic ascending UUID sorting")
    void shouldPartitionAndSortChunksDeterministically() {
        // Create 600 unsorted bundle variant IDs (to produce chunks of 250, 250, 100)
        List<UUID> unsortedBundleIds = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            unsortedBundleIds.add(UUID.randomUUID());
        }
        when(bundleComponentRepository.findBundleVariantIdsByComponentSku(componentSku))
                .thenReturn(unsortedBundleIds);

        // Execute batch masking cascade
        BatchComponentCoordinator.BatchCascadeResult result = coordinator.maskComponentInBundles(componentSku);

        // Verification:
        assertEquals(componentSku, result.componentSku());
        assertEquals(600, result.totalBundlesAffected());
        assertEquals(3, result.chunksProcessed(), "600 items must be partitioned into 3 chunks (250, 250, 100)");

        // 1. Verify instant <2ms blacklist notification was called
        verify(notificationPort).notifyComponentBlacklisted(componentSku);

        // 2. Verify SQL updates: 3 chunks for variants + 3 chunks for companion family sync = 6 jdbc updates
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcOperations, times(6)).update(anyString(), paramCaptor.capture());

        List<MapSqlParameterSource> capturedParams = paramCaptor.getAllValues();
        // Check first chunk (indices 0 and 1 correspond to chunk 1 variant update and family sync)
        @SuppressWarnings("unchecked")
        List<UUID> chunk1 = (List<UUID>) capturedParams.get(0).getValue("ids");
        assertEquals(250, chunk1.size());

        // Verify deterministic ascending order
        List<UUID> sortedCopy = new ArrayList<>(chunk1);
        Collections.sort(sortedCopy);
        assertEquals(sortedCopy, chunk1, "Chunk IDs must be strictly sorted in ascending order for deterministic lock acquisition");

        // 3. Verify notification port was notified for each chunk
        verify(notificationPort, times(3)).notifyBundlesMasked(anyList());
    }

    @Test
    @DisplayName("Should handle scenario with zero parent bundles gracefully")
    void shouldHandleZeroParentBundles() {
        when(bundleComponentRepository.findBundleVariantIdsByComponentSku(componentSku))
                .thenReturn(List.of());

        BatchComponentCoordinator.BatchCascadeResult result = coordinator.maskComponentInBundles(componentSku);

        assertEquals(0, result.totalBundlesAffected());
        assertEquals(0, result.chunksProcessed());
        verify(notificationPort).notifyComponentBlacklisted(componentSku);
        verify(jdbcOperations, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("Should validate component SKU parameter")
    void shouldValidateComponentSku() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.maskComponentInBundles(null));
        assertThrows(IllegalArgumentException.class, () -> coordinator.maskComponentInBundles("   "));
    }
}
