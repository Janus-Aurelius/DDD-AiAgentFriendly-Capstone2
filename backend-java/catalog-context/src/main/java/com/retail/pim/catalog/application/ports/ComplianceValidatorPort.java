package com.retail.pim.catalog.application.ports;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;

import java.util.Map;
import java.util.UUID;

/**
 * Outgoing port for compliance validation across context boundaries.
 * Implemented in-process by ComplianceEvaluationFacade in compliance-context.
 */
public interface ComplianceValidatorPort {

    boolean validateVariantRevision(UUID revisionId, Map<String, AttributeValue> remediatedAttributes);
}
