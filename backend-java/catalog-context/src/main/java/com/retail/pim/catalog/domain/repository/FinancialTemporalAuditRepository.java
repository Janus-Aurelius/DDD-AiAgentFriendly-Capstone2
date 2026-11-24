package com.retail.pim.catalog.domain.repository;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain Repository Port for Bi-Temporal Financial & Logistics Historical Auditing (CUJ 7).
 */
public interface FinancialTemporalAuditRepository {
    FinancialAttributeHistory save(FinancialAttributeHistory history);
    Optional<FinancialAttributeHistory> findHistoricalSnapshot(String sku, String uomTier, Instant asOf);
    Optional<FinancialAttributeHistory> findCurrentActive(String sku, String uomTier);
    List<FinancialAttributeHistory> findHistoryBySku(String sku);
}
