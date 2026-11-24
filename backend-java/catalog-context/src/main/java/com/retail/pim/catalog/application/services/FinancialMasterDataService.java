package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import com.retail.pim.catalog.domain.repository.FinancialTemporalAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating bi-temporal close-and-insert semantics for financial & logistics attributes (CUJ 7).
 * Ensures that historical queries (e.g. carrier freight audit, accounting tax reconciliation)
 * reconstruct the exact active state as of any past timestamp.
 */
@Service
public class FinancialMasterDataService implements FinancialMasterDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinancialMasterDataService.class);

    private final FinancialTemporalAuditRepository financialAuditRepository;

    public FinancialMasterDataService(FinancialTemporalAuditRepository financialAuditRepository) {
        this.financialAuditRepository = financialAuditRepository;
    }

    /**
     * Bi-temporal close-and-insert:
     * 1. Closes the currently active record by setting effective_to = effectiveFrom.
     * 2. Inserts a new record active from effectiveFrom with effective_to = INFINITY.
     */
    @Override
    @Transactional
    public FinancialAttributeHistory recordFinancialAttributes(
            String sku,
            String uomTier,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal grossWeightKg,
            String taxBand,
            String customsHsnCode,
            Instant effectiveFrom) {

        Instant effectiveDate = effectiveFrom != null ? effectiveFrom : Instant.now();

        // 1. Close current active period if it exists
        Optional<FinancialAttributeHistory> currentActive = financialAuditRepository.findCurrentActive(sku, uomTier);
        if (currentActive.isPresent()) {
            FinancialAttributeHistory existing = currentActive.get();
            existing.closeEffectivePeriod(effectiveDate);
            financialAuditRepository.save(existing);
            log.info("Closed previous active period for SKU [{}], UOM [{}] at [{}]", sku, uomTier, effectiveDate);
        }

        // 2. Insert new historical snapshot
        FinancialAttributeHistory newRecord = new FinancialAttributeHistory(
                UUID.randomUUID(),
                sku,
                uomTier,
                lengthCm,
                widthCm,
                heightCm,
                grossWeightKg,
                taxBand,
                customsHsnCode,
                effectiveDate,
                FinancialAttributeHistory.INFINITY,
                Instant.now()
        );

        FinancialAttributeHistory saved = financialAuditRepository.save(newRecord);
        log.info("Created new temporal financial record for SKU [{}], UOM [{}], effectiveFrom [{}]",
                sku, uomTier, effectiveDate);

        return saved;
    }

    /**
     * Executes point-in-time temporal snapshot query.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialAttributeHistory> getHistoricalSnapshot(String sku, String uomTier, Instant asOf) {
        Instant queryTime = asOf != null ? asOf : Instant.now();
        return financialAuditRepository.findHistoricalSnapshot(sku, uomTier, queryTime);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialAttributeHistory> getCurrentActive(String sku, String uomTier) {
        return financialAuditRepository.findCurrentActive(sku, uomTier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialAttributeHistory> getHistory(String sku) {
        return financialAuditRepository.findHistoryBySku(sku);
    }
}
