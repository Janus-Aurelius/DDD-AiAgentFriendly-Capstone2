package com.retail.pim.dam.infrastructure.persistence;

import com.retail.pim.dam.infrastructure.persistence.entity.DigitalAssetJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataDigitalAssetRepository extends JpaRepository<DigitalAssetJpaEntity, UUID> {

    List<DigitalAssetJpaEntity> findByActiveTrue();

    @Query(value = "SELECT * FROM dam.digital_asset WHERE is_active = true AND license->>'expirationDate' IS NOT NULL", nativeQuery = true)
    List<DigitalAssetJpaEntity> findActiveAssetsWithExpiringLicenses();

    @Query(value = "SELECT * FROM dam.digital_asset WHERE linked_product_ids @> CAST(:productIdJson AS jsonb)", nativeQuery = true)
    List<DigitalAssetJpaEntity> findByProductIdJson(@Param("productIdJson") String productIdJson);
}
