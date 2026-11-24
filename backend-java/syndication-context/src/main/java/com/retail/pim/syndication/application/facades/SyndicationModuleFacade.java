package com.retail.pim.syndication.application.facades;

import com.retail.pim.catalog.application.ports.BundleMaskNotificationPort;

import java.util.List;
import java.util.UUID;

/**
 * Public Application Facade exposed by syndication-context for bundle availability and read-model masking.
 */
public interface SyndicationModuleFacade extends BundleMaskNotificationPort {

    void bulkMaskBundles(List<UUID> bundleVariantIds);

    void blacklistComponent(String componentSku);

    boolean isBundleAvailable(List<String> componentSkus);

    List<String> getBlacklistedComponents(List<String> componentSkus);
}
