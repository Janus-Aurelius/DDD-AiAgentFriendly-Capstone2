package com.retail.pim.catalog.application.services;

import com.retail.pim.catalog.domain.model.VariantRevision;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;

import java.util.Map;
import java.util.UUID;

/**
 * Primary Use Case interface defining variant revision operations.
 */
public interface VariantRevisionUseCase {

    VariantRevision createWorkingRevision(String sku);

    VariantRevision updateWorkingRevision(UUID revisionId, Map<String, AttributeValue> remediatedAttributes);

    VariantRevision submitForReview(UUID revisionId);

    VariantRevision promoteRevision(UUID revisionId);

    VariantRevision getRevision(UUID revisionId);
}
