package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use case interface for bi-temporal financial and logistics master data management (CUJ 7).
 */
public interface FinancialMasterDataUseCase {

    FinancialAttributeHistory recordFinancialAttributes(
            String sku,
            String uomTier,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal grossWeightKg,
            String taxBand,
            String customsHsnCode,
            Instant effectiveFrom
    );

    Optional<FinancialAttributeHistory> getHistoricalSnapshot(String sku, String uomTier, Instant asOf);

    Optional<FinancialAttributeHistory> getCurrentActive(String sku, String uomTier);

    List<FinancialAttributeHistory> getHistory(String sku);
}
