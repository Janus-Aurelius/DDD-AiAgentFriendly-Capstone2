package com.retail.pim.catalog.application.facades;

import com.retail.pim.catalog.domain.model.Variant;

/**
 * Public Application Facade for operational warehouse quarantine certifications (CUJ 8).
 */
public interface CatalogQuarantineFacade {

    /**
     * Operational exit bypass: transitions variant directly from SKU_RECALLED_LOT_HOLD back to SKU_ACTIVE
     * upon WMS physical warehouse quarantine certification without requiring re-authoring of catalog metadata.
     */
    Variant certifyLotQuarantine(String sku, String lotNumber);
}
