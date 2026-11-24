package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.infrastructure.persistence.entity.FinancialAttributeHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataFinancialAttributeHistoryJpaRepository
        extends JpaRepository<FinancialAttributeHistoryJpaEntity, UUID> {

    @Query("SELECT h FROM FinancialAttributeHistoryJpaEntity h WHERE UPPER(h.sku) = UPPER(:sku) AND UPPER(h.uomTier) = UPPER(:uomTier) AND h.effectiveFrom <= :asOf AND h.effectiveTo > :asOf ORDER BY h.effectiveFrom DESC")
    List<FinancialAttributeHistoryJpaEntity> findHistoricalSnapshots(
            @Param("sku") String sku,
            @Param("uomTier") String uomTier,
            @Param("asOf") Instant asOf
    );

    @Query("SELECT h FROM FinancialAttributeHistoryJpaEntity h WHERE UPPER(h.sku) = UPPER(:sku) AND UPPER(h.uomTier) = UPPER(:uomTier) AND h.effectiveTo >= :infinity ORDER BY h.effectiveFrom DESC")
    List<FinancialAttributeHistoryJpaEntity> findCurrentActiveSnapshots(
            @Param("sku") String sku,
            @Param("uomTier") String uomTier,
            @Param("infinity") Instant infinity
    );

    List<FinancialAttributeHistoryJpaEntity> findBySkuOrderByEffectiveFromDesc(String sku);
}
