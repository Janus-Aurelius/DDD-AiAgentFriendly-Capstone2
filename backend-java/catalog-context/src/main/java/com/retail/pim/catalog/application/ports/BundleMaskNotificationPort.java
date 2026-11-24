package com.retail.pim.catalog.application.ports;

import java.util.List;
import java.util.UUID;

/**
 * Outgoing port to notify syndication and storefront read-model systems of component blacklists and bundle masking.
 */
public interface BundleMaskNotificationPort {

    void notifyComponentBlacklisted(String componentSku);

    void notifyBundlesMasked(List<UUID> bundleVariantIds);
}
