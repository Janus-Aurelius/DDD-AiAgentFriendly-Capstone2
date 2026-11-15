package com.retail.pim.dam.infrastructure.persistence;

import com.retail.pim.dam.domain.model.DigitalAsset;
import com.retail.pim.dam.domain.repository.DigitalAssetRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter: Implements DigitalAssetRepository.
 * Stores asset aggregate state (metadata, license, linked products, renditions).
 */
@Repository
public class PostgresDigitalAssetRepository implements DigitalAssetRepository {

    // Thread-safe repository store (persisting domain state in memory/database)
    private final Map<UUID, DigitalAsset> assetStorage = new ConcurrentHashMap<>();

    @Override
    public DigitalAsset save(DigitalAsset asset) {
        assetStorage.put(asset.getId(), asset);
        return asset;
    }

    @Override
    public Optional<DigitalAsset> findById(UUID id) {
        return Optional.ofNullable(assetStorage.get(id));
    }

    @Override
    public List<DigitalAsset> findActiveAssetsWithLicenses() {
        return assetStorage.values().stream()
                .filter(DigitalAsset::isActive)
                .toList();
    }

    @Override
    public List<DigitalAsset> findByProductId(UUID productId) {
        return assetStorage.values().stream()
                .filter(a -> a.getLinkedProductIds().contains(productId))
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        assetStorage.remove(id);
    }
}
