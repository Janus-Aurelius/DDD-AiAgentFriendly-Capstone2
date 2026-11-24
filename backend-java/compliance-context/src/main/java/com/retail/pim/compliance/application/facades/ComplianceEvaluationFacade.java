package com.retail.pim.compliance.application.facades;

import com.retail.pim.catalog.application.ports.ComplianceValidatorPort;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Public Application Facade for compliance evaluations across contexts.
 * In-process implementation of ComplianceValidatorPort.
 */
@Service
public class ComplianceEvaluationFacade implements ComplianceValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEvaluationFacade.class);

    @Override
    public boolean validateVariantRevision(UUID revisionId, Map<String, AttributeValue> remediatedAttributes) {
        log.info("Evaluating compliance for revisionId [{}] with {} remediated attributes",
                revisionId, remediatedAttributes != null ? remediatedAttributes.size() : 0);

        if (remediatedAttributes == null || remediatedAttributes.isEmpty()) {
            return false;
        }

        // Prop 65 California compliance requires warning/disclosure attributes
        if (remediatedAttributes.containsKey("prop_65_warning") ||
            remediatedAttributes.containsKey("prop_65_label") ||
            remediatedAttributes.containsKey("prop_65_compliant") ||
            remediatedAttributes.containsKey("chemical_disclosure")) {
            return true;
        }

        // Generic compliance validation: attributes must contain non-blank values
        return remediatedAttributes.values().stream()
                .anyMatch(v -> v != null && v.getValue() != null && !String.valueOf(v.getValue()).isBlank());
    }
}
