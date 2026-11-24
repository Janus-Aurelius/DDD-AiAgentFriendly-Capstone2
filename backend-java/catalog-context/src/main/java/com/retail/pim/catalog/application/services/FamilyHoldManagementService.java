package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.shared.exceptions.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Service managing administrative and legal holds on Product Families (CUJ 6).
 * Supports concurrent JSONB set hold coexistence and version-checked optimistic concurrency.
 */
@Service
public class FamilyHoldManagementService implements FamilyHoldUseCase {

    private static final Logger log = LoggerFactory.getLogger(FamilyHoldManagementService.class);

    private final ProductRepository productRepository;

    public FamilyHoldManagementService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Applies an administrative/legal hold to the Product Family.
     * Enforces optimistic concurrency check if expectedVersion is provided.
     */
    @Transactional
    public ProductFamily applyHold(UUID familyId, String reason, Long expectedVersion) {
        ProductFamily family = productRepository.findById(familyId)
                .orElseThrow(() -> new DomainException("Product family not found: " + familyId));

        if (expectedVersion != null && !expectedVersion.equals(family.getVersion())) {
            throw new DomainException(String.format(
                    "Optimistic lock conflict on family [%s]: expected version [%d] but was [%d]",
                    familyId, expectedVersion, family.getVersion()));
        }

        family.applyHold(reason);
        log.info("Applied hold [{}] to family [{}]. Current state: [{}], active holds: {}",
                reason, family.getCode(), family.getLifecycleState(), family.getActiveSuspensionReasons());

        return productRepository.save(family);
    }

    /**
     * Lifts an administrative/legal hold from the Product Family.
     * Enforces optimistic concurrency check if expectedVersion is provided.
     */
    @Transactional
    public ProductFamily liftHold(UUID familyId, String reason, Long expectedVersion) {
        ProductFamily family = productRepository.findById(familyId)
                .orElseThrow(() -> new DomainException("Product family not found: " + familyId));

        if (expectedVersion != null && !expectedVersion.equals(family.getVersion())) {
            throw new DomainException(String.format(
                    "Optimistic lock conflict on family [%s]: expected version [%d] but was [%d]",
                    familyId, expectedVersion, family.getVersion()));
        }

        family.liftHold(reason);
        log.info("Lifted hold [{}] from family [{}]. Current state: [{}], remaining holds: {}",
                reason, family.getCode(), family.getLifecycleState(), family.getActiveSuspensionReasons());

        return productRepository.save(family);
    }

    /**
     * Retrieves all active suspension reasons for the given product family.
     */
    @Transactional(readOnly = true)
    public Set<String> getActiveHolds(UUID familyId) {
        ProductFamily family = productRepository.findById(familyId)
                .orElseThrow(() -> new DomainException("Product family not found: " + familyId));
        return family.getActiveSuspensionReasons();
    }
}
