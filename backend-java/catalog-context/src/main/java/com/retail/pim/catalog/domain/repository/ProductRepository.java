package com.retail.pim.catalog.domain.repository;

import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Repository interface defined by the Domain layer.
 * Pure Java - completely decoupled from database mechanics.
 */
public interface ProductRepository {

    ProductFamily save(ProductFamily productFamily);

    Optional<ProductFamily> findById(UUID id);

    Optional<ProductFamily> findByCode(String code);

    List<ProductFamily> findByStatus(ProductLifecycleState status, int page, int size);

    List<ProductFamily> findAll(int page, int size);

    void deleteById(UUID id);

    boolean existsByCode(String code);
}
