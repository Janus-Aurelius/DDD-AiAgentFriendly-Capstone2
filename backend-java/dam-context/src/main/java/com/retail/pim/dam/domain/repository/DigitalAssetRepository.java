package com.retail.pim.dam.domain.repository;

import com.retail.pim.dam.domain.model.DigitalAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DigitalAssetRepository {

    DigitalAsset save(DigitalAsset asset);

    Optional<DigitalAsset> findById(UUID id);

    List<DigitalAsset> findActiveAssetsWithLicenses();

    List<DigitalAsset> findByProductId(UUID productId);

    void deleteById(UUID id);
}
