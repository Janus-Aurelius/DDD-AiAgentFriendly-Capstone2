package com.retail.pim.catalog.domain.service;

import com.retail.pim.catalog.domain.model.ProductFamily;

import java.util.List;

/**
 * Domain Service Port: Hard gate verifying compliance publishability before an aggregate
 * can transition to PUBLISHED or be syndicated to sales channels.
 */
public interface PublishabilityGate {

    /**
     * Verifies that the product has ZERO blocking compliance violations for the specified target scope.
     * If the background compliance report is missing or lagging behind the aggregate's current version,
     * implementations execute an immediate synchronous read-through evaluation.
     */
    boolean canPublish(ProductFamily product, String targetLocale, String targetChannel);

    /**
     * Returns the list of blocking violation messages preventing publication.
     */
    List<String> getBlockingViolations(ProductFamily product, String targetLocale, String targetChannel);
}
