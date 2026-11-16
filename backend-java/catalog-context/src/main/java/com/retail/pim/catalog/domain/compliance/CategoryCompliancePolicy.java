package com.retail.pim.catalog.domain.compliance;

import com.retail.pim.catalog.domain.model.ProductFamily;

/**
 * Domain Policy / Specification: Encapsulates category-specific regulatory compliance rules.
 * Eliminates hardcoded category logic from the ProductFamily Aggregate Root (Open-Closed Principle).
 */
public interface CategoryCompliancePolicy {

    /**
     * Determines whether this compliance policy applies to the given product.
     */
    boolean supports(ProductFamily product);

    /**
     * Evaluates compliance invariants. Throws DomainException if any compliance rule is violated.
     */
    void validateCompliance(ProductFamily product);
}
