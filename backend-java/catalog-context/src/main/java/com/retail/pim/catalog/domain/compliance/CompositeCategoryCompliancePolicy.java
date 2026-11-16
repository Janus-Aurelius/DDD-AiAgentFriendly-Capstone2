package com.retail.pim.catalog.domain.compliance;

import com.retail.pim.catalog.domain.model.ProductFamily;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Composite Policy: Dispatches compliance evaluation to all matching category policies.
 */
@Component
@Primary
public class CompositeCategoryCompliancePolicy implements CategoryCompliancePolicy {

    private final List<CategoryCompliancePolicy> policies;

    public CompositeCategoryCompliancePolicy(List<CategoryCompliancePolicy> policies) {
        this.policies = policies;
    }

    @Override
    public boolean supports(ProductFamily product) {
        return true;
    }

    @Override
    public void validateCompliance(ProductFamily product) {
        for (CategoryCompliancePolicy policy : policies) {
            if (policy != this && policy.supports(product)) {
                policy.validateCompliance(product);
            }
        }
    }
}
