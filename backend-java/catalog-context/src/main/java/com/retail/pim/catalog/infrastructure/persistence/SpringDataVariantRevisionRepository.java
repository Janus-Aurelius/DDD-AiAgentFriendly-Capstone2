package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.infrastructure.persistence.entity.VariantRevisionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataVariantRevisionRepository extends JpaRepository<VariantRevisionJpaEntity, UUID> {

    List<VariantRevisionJpaEntity> findByVariantId(UUID variantId);

    @Query("SELECT r FROM VariantRevisionJpaEntity r WHERE r.variantId = :variantId AND r.revisionState = :state ORDER BY r.createdAt DESC")
    List<VariantRevisionJpaEntity> findByVariantIdAndState(@Param("variantId") UUID variantId, @Param("state") RevisionState state);
}
