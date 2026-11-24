package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.infrastructure.persistence.entity.BundleComponentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataBundleComponentJpaRepository extends JpaRepository<BundleComponentJpaEntity, UUID> {

    @Query("SELECT bc.bundleVariantId FROM BundleComponentJpaEntity bc WHERE bc.componentSku = :componentSku")
    List<UUID> findBundleVariantIdsByComponentSku(@Param("componentSku") String componentSku);

    @Query("SELECT bc.componentSku FROM BundleComponentJpaEntity bc WHERE bc.bundleVariantId = :bundleVariantId")
    List<String> findComponentSkusByBundleVariantId(@Param("bundleVariantId") UUID bundleVariantId);

    List<BundleComponentJpaEntity> findByBundleVariantId(UUID bundleVariantId);

    void deleteByBundleVariantId(UUID bundleVariantId);
}
