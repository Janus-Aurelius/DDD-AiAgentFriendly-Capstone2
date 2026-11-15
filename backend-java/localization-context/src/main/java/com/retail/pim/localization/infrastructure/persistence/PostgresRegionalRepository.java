package com.retail.pim.localization.infrastructure.persistence;

import com.retail.pim.localization.domain.model.RegionalCatalogItem;
import com.retail.pim.localization.domain.repository.RegionalCatalogRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter: Implements RegionalCatalogRepository.
 * Manages regional catalog item aggregates and regional override mappings.
 */
@Repository
public class PostgresRegionalRepository implements RegionalCatalogRepository {

    private final Map<UUID, RegionalCatalogItem> storage = new ConcurrentHashMap<>();

    @Override
    public RegionalCatalogItem save(RegionalCatalogItem item) {
        storage.put(item.getId(), item);
        return item;
    }

    @Override
    public Optional<RegionalCatalogItem> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<RegionalCatalogItem> findByGlobalProductAndRegion(UUID globalProductId, String regionCode) {
        return storage.values().stream()
                .filter(i -> i.getGlobalProductId().equals(globalProductId) &&
                             i.getRegionCode().equalsIgnoreCase(regionCode))
                .findFirst();
    }

    @Override
    public List<RegionalCatalogItem> findByRegion(String regionCode) {
        return storage.values().stream()
                .filter(i -> i.getRegionCode().equalsIgnoreCase(regionCode))
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        storage.remove(id);
    }
}
