package com.retail.pim.catalog.domain.compliance;

import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.exceptions.DomainException;
import org.springframework.stereotype.Component;

/**
 * Compliance Policy for Electronics:
 * Validates mandatory energy ratings and regulatory certifications.
 */
@Component
public class ElectronicsCompliancePolicy implements CategoryCompliancePolicy {

    @Override
    public boolean supports(ProductFamily product) {
        AttributeValue flag = product.getAttributes().get("is_electronics");
        if (flag != null && flag.asBoolean()) {
            return true;
        }
        AttributeValue familyType = product.getAttributes().get("family_type");
        return familyType != null && familyType.asString().toLowerCase().contains("electronic");
    }

    @Override
    public void validateCompliance(ProductFamily product) {
        AttributeValue energyRating = product.getAttributes().get("energy_rating");
        if (energyRating == null || energyRating.asString().isBlank()) {
            throw new DomainException(String.format(
                    "Compliance failure for product '%s': Certified 'energy_rating' attribute is required for Electronics before approval",
                    product.getCode()));
        }
    }
}
