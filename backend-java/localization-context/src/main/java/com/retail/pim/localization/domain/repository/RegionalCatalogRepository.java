package com.retail.pim.localization.domain.repository;

import com.retail.pim.localization.domain.model.RegionalCatalogItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegionalCatalogRepository {

    RegionalCatalogItem save(RegionalCatalogItem item);

    Optional<RegionalCatalogItem> findById(UUID id);

    Optional<RegionalCatalogItem> findByGlobalProductAndRegion(UUID globalProductId, String regionCode);

    List<RegionalCatalogItem> findByRegion(String regionCode);

    void deleteById(UUID id);
}
