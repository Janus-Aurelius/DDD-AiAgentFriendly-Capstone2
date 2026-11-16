package com.retail.pim.localization.infrastructure.persistence;

import com.retail.pim.localization.infrastructure.persistence.entity.RegionalCatalogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataRegionalCatalogRepository extends JpaRepository<RegionalCatalogJpaEntity, UUID> {

    Optional<RegionalCatalogJpaEntity> findByGlobalProductIdAndRegionCode(UUID globalProductId, String regionCode);

    List<RegionalCatalogJpaEntity> findByGlobalProductId(UUID globalProductId);

    List<RegionalCatalogJpaEntity> findByRegionCode(String regionCode);
}
