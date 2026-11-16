package com.retail.pim.catalog.domain.compliance;

import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.exceptions.DomainException;
import org.springframework.stereotype.Component;

/**
 * Compliance Policy for Apparel & Garments:
 * Validates mandatory material composition and care instructions.
 */
@Component
public class ApparelCompliancePolicy implements CategoryCompliancePolicy {

    @Override
    public boolean supports(ProductFamily product) {
        AttributeValue flag = product.getAttributes().get("is_apparel");
        if (flag != null && flag.asBoolean()) {
            return true;
        }
        AttributeValue familyType = product.getAttributes().get("family_type");
        return familyType != null && (familyType.asString().toLowerCase().contains("apparel") ||
                                      familyType.asString().toLowerCase().contains("clothing"));
    }

    @Override
    public void validateCompliance(ProductFamily product) {
        AttributeValue fabricComp = product.getAttributes().get("fabric_composition");
        if (fabricComp == null || fabricComp.asString().isBlank()) {
            throw new DomainException(String.format(
                    "Compliance failure for product '%s': Apparel requires mandatory 'fabric_composition' attribute",
                    product.getCode()));
        }
    }
}
