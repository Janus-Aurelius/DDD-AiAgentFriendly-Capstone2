package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import com.retail.pim.catalog.domain.repository.FinancialTemporalAuditRepository;
import com.retail.pim.catalog.infrastructure.persistence.entity.FinancialAttributeHistoryJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adapter: PostgreSQL implementation of FinancialTemporalAuditRepository port.
 */
@Repository
public class PostgresFinancialTemporalAuditRepository implements FinancialTemporalAuditRepository {

    private final SpringDataFinancialAttributeHistoryJpaRepository jpaRepository;

    public PostgresFinancialTemporalAuditRepository(SpringDataFinancialAttributeHistoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FinancialAttributeHistory save(FinancialAttributeHistory history) {
        FinancialAttributeHistoryJpaEntity entity = toEntity(history);
        FinancialAttributeHistoryJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<FinancialAttributeHistory> findHistoricalSnapshot(String sku, String uomTier, Instant asOf) {
        List<FinancialAttributeHistoryJpaEntity> results = jpaRepository.findHistoricalSnapshots(sku, uomTier, asOf);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDomain(results.get(0)));
    }

    @Override
    public Optional<FinancialAttributeHistory> findCurrentActive(String sku, String uomTier) {
        List<FinancialAttributeHistoryJpaEntity> results = jpaRepository.findCurrentActiveSnapshots(
                sku, uomTier, FinancialAttributeHistory.INFINITY.minusSeconds(86400));
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDomain(results.get(0)));
    }

    @Override
    public List<FinancialAttributeHistory> findHistoryBySku(String sku) {
        return jpaRepository.findBySkuOrderByEffectiveFromDesc(sku).stream()
                .map(this::toDomain)
                .toList();
    }

    private FinancialAttributeHistoryJpaEntity toEntity(FinancialAttributeHistory domain) {
        FinancialAttributeHistoryJpaEntity entity = new FinancialAttributeHistoryJpaEntity();
        entity.setId(domain.getId());
        entity.setSku(domain.getSku());
        entity.setUomTier(domain.getUomTier());
        entity.setLengthCm(domain.getLengthCm());
        entity.setWidthCm(domain.getWidthCm());
        entity.setHeightCm(domain.getHeightCm());
        entity.setGrossWeightKg(domain.getGrossWeightKg());
        entity.setTaxBand(domain.getTaxBand());
        entity.setCustomsHsnCode(domain.getCustomsHsnCode());
        entity.setEffectiveFrom(domain.getEffectiveFrom());
        entity.setEffectiveTo(domain.getEffectiveTo());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    private FinancialAttributeHistory toDomain(FinancialAttributeHistoryJpaEntity entity) {
        return new FinancialAttributeHistory(
                entity.getId(),
                entity.getSku(),
                entity.getUomTier(),
                entity.getLengthCm(),
                entity.getWidthCm(),
                entity.getHeightCm(),
                entity.getGrossWeightKg(),
                entity.getTaxBand(),
                entity.getCustomsHsnCode(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getCreatedAt()
        );
    }
}
