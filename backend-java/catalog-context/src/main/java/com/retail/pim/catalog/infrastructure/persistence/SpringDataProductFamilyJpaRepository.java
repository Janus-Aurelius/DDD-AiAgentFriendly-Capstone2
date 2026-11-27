package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ProductJpaEntity.
 */
@Repository
public interface SpringDataProductFamilyJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<ProductJpaEntity> findByStatus(ProductLifecycleState status, Pageable pageable);

    @Query("SELECT p FROM ProductJpaEntity p JOIN p.variants v WHERE v.sku = :sku")
    Optional<ProductJpaEntity> findBySku(@Param("sku") String sku);

    @Query("SELECT p FROM ProductJpaEntity p JOIN p.variants v WHERE v.id = :variantId")
    Optional<ProductJpaEntity> findByVariantId(@Param("variantId") UUID variantId);
}
