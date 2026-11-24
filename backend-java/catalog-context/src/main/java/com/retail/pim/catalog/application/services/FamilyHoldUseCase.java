package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.model.ProductFamily;

import java.util.Set;
import java.util.UUID;

/**
 * Use case port for applying and lifting administrative and legal holds on Product Families (CUJ 6).
 */
public interface FamilyHoldUseCase {
    ProductFamily applyHold(UUID familyId, String reason, Long expectedVersion);
    ProductFamily liftHold(UUID familyId, String reason, Long expectedVersion);
    Set<String> getActiveHolds(UUID familyId);
}
