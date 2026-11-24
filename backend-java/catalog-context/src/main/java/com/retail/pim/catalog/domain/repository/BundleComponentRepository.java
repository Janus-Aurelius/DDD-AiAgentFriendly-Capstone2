package com.retail.pim.catalog.domain.repository;

import com.retail.pim.catalog.domain.model.BundleComponent;

import java.util.List;
import java.util.UUID;

/**
 * Domain repository port for bundle component relationships.
 */
public interface BundleComponentRepository {

    BundleComponent save(BundleComponent component);

    List<BundleComponent> saveAll(List<BundleComponent> components);

    List<UUID> findBundleVariantIdsByComponentSku(String componentSku);

    List<String> findComponentSkusByBundleVariantId(UUID bundleVariantId);

    List<BundleComponent> findByBundleVariantId(UUID bundleVariantId);

    void deleteByBundleVariantId(UUID bundleVariantId);
}
