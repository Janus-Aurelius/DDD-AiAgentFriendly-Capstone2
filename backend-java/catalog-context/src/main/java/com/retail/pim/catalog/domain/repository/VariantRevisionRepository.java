package com.retail.pim.catalog.domain.repository;

import com.retail.pim.catalog.domain.model.VariantRevision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantRevisionRepository {

    VariantRevision save(VariantRevision revision);

    Optional<VariantRevision> findById(UUID id);

    List<VariantRevision> findByVariantId(UUID variantId);

    Optional<VariantRevision> findActiveDraftByVariantId(UUID variantId);
}
