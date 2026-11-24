package com.retail.pim.catalog.application.facades;

/**
 * Public Application Facade exposed by catalog-context for emergency recall quarantine.
 */
public interface CatalogRecallFacade {

    void quarantineVariant(String sku, String lotNumber, String reason);
}
