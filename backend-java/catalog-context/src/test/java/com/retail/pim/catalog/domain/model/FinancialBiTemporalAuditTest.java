package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.application.services.FinancialMasterDataService;
import com.retail.pim.catalog.domain.repository.FinancialTemporalAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slice 7: Financial Bi-Temporal Historical Auditing Tests (CUJ 7)")
class FinancialBiTemporalAuditTest {

    private InMemoryFinancialTemporalAuditRepository repository;
    private FinancialMasterDataService financialService;

    private final String sku = "SKU-JCE-ORANGE";
    private final String uomTier = "MASTER_CARTON";

    @BeforeEach
    void setUp() {
        repository = new InMemoryFinancialTemporalAuditRepository();
        financialService = new FinancialMasterDataService(repository);
    }

    @Test
    @DisplayName("CUJ 7: Historical freight audit and accounting tax reconciliation with bi-temporal snapshots")
    void biTemporalAuditHistoryQueries() {
        Instant oct1 = Instant.parse("2026-10-01T00:00:00Z");
        Instant nov1 = Instant.parse("2026-11-01T00:00:00Z");
        Instant nov10 = Instant.parse("2026-11-10T00:00:00Z");

        // 1. Initial configuration on Oct 1: MASTER_CARTON weight = 12.0 kg, tax band = ESSENTIAL_GROCERIES
        financialService.recordFinancialAttributes(
                sku, uomTier, new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("25.00"),
                new BigDecimal("12.000"), "ESSENTIAL_GROCERIES", "HSN-2009-10", oct1
        );

        // 2. Correction on Nov 1: Dimensional gross weight corrected from 12.0 kg to 14.0 kg
        financialService.recordFinancialAttributes(
                sku, uomTier, new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("25.00"),
                new BigDecimal("14.000"), "ESSENTIAL_GROCERIES", "HSN-2009-10", nov1
        );

        // 3. Tax Classification shift on Nov 10: Tax band shifted from ESSENTIAL_GROCERIES to STANDARD_VAT
        financialService.recordFinancialAttributes(
                sku, uomTier, new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("25.00"),
                new BigDecimal("14.000"), "STANDARD_VAT", "HSN-2009-10", nov10
        );

        // --- Audit Verification (CUJ 7) ---

        // Verification 1: Finance audits carrier freight invoice dated October 25
        Instant asOfOct25 = Instant.parse("2026-10-25T14:30:00Z");
        FinancialAttributeHistory oct25Snapshot = financialService.getHistoricalSnapshot(sku, uomTier, asOfOct25).orElseThrow();
        assertThat(oct25Snapshot.getGrossWeightKg()).isEqualByComparingTo("12.000");
        assertThat(oct25Snapshot.getTaxBand()).isEqualTo("ESSENTIAL_GROCERIES");
        assertThat(oct25Snapshot.getEffectiveFrom()).isEqualTo(oct1);
        assertThat(oct25Snapshot.getEffectiveTo()).isEqualTo(nov1);

        // Verification 2: Accounting reconciles customer return for order placed on November 5
        Instant asOfNov5 = Instant.parse("2026-11-05T09:15:00Z");
        FinancialAttributeHistory nov5Snapshot = financialService.getHistoricalSnapshot(sku, uomTier, asOfNov5).orElseThrow();
        assertThat(nov5Snapshot.getGrossWeightKg()).isEqualByComparingTo("14.000");
        assertThat(nov5Snapshot.getTaxBand()).isEqualTo("ESSENTIAL_GROCERIES");
        assertThat(nov5Snapshot.getEffectiveFrom()).isEqualTo(nov1);
        assertThat(nov5Snapshot.getEffectiveTo()).isEqualTo(nov10);

        // Verification 3: Current active state as of November 15
        Instant asOfNov15 = Instant.parse("2026-11-15T16:00:00Z");
        FinancialAttributeHistory nov15Snapshot = financialService.getHistoricalSnapshot(sku, uomTier, asOfNov15).orElseThrow();
        assertThat(nov15Snapshot.getGrossWeightKg()).isEqualByComparingTo("14.000");
        assertThat(nov15Snapshot.getTaxBand()).isEqualTo("STANDARD_VAT");
        assertThat(nov15Snapshot.getEffectiveFrom()).isEqualTo(nov10);
        assertThat(nov15Snapshot.getEffectiveTo()).isEqualTo(FinancialAttributeHistory.INFINITY);
    }

    @Test
    @DisplayName("Bi-temporal Close-and-Insert: Preceding period is closed exactly at effectiveFrom of successor")
    void closeAndInsertMaintainsContinuity() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

        financialService.recordFinancialAttributes(
                sku, "EACH", new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("5.00"),
                new BigDecimal("0.500"), "STANDARD_VAT", "HSN-1234", t1
        );

        financialService.recordFinancialAttributes(
                sku, "EACH", new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("5.00"),
                new BigDecimal("0.600"), "STANDARD_VAT", "HSN-1234", t2
        );

        List<FinancialAttributeHistory> history = financialService.getHistory(sku);
        assertThat(history).hasSize(2);

        FinancialAttributeHistory current = history.stream().filter(h -> h.getEffectiveTo().equals(FinancialAttributeHistory.INFINITY)).findFirst().orElseThrow();
        FinancialAttributeHistory previous = history.stream().filter(h -> !h.getEffectiveTo().equals(FinancialAttributeHistory.INFINITY)).findFirst().orElseThrow();

        assertThat(previous.getEffectiveTo()).isEqualTo(current.getEffectiveFrom());
        assertThat(previous.getGrossWeightKg()).isEqualByComparingTo("0.500");
        assertThat(current.getGrossWeightKg()).isEqualByComparingTo("0.600");
    }

    private static class InMemoryFinancialTemporalAuditRepository implements FinancialTemporalAuditRepository {
        private final List<FinancialAttributeHistory> storage = new ArrayList<>();

        @Override
        public FinancialAttributeHistory save(FinancialAttributeHistory history) {
            storage.removeIf(h -> h.getId().equals(history.getId()));
            storage.add(history);
            return history;
        }

        @Override
        public Optional<FinancialAttributeHistory> findHistoricalSnapshot(String sku, String uomTier, Instant asOf) {
            return storage.stream()
                    .filter(h -> h.getSku().equalsIgnoreCase(sku) && h.getUomTier().equalsIgnoreCase(uomTier))
                    .filter(h -> h.isEffectiveAt(asOf))
                    .max(Comparator.comparing(FinancialAttributeHistory::getEffectiveFrom));
        }

        @Override
        public Optional<FinancialAttributeHistory> findCurrentActive(String sku, String uomTier) {
            return storage.stream()
                    .filter(h -> h.getSku().equalsIgnoreCase(sku) && h.getUomTier().equalsIgnoreCase(uomTier))
                    .filter(h -> h.getEffectiveTo().equals(FinancialAttributeHistory.INFINITY))
                    .max(Comparator.comparing(FinancialAttributeHistory::getEffectiveFrom));
        }

        @Override
        public List<FinancialAttributeHistory> findHistoryBySku(String sku) {
            return storage.stream()
                    .filter(h -> h.getSku().equalsIgnoreCase(sku))
                    .sorted(Comparator.comparing(FinancialAttributeHistory::getEffectiveFrom).reversed())
                    .toList();
        }
    }
}
