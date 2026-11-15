package com.retail.pim.taxonomy.domain.repository;

import com.retail.pim.taxonomy.domain.model.CategoryNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Repository for hierarchical Category taxonomy.
 * Pure Java interface.
 */
public interface CategoryRepository {

    CategoryNode save(CategoryNode node);

    Optional<CategoryNode> findById(UUID id);

    Optional<CategoryNode> findByCode(String code);

    List<CategoryNode> findAllRoots();

    List<CategoryNode> findAll();

    List<CategoryNode> findAncestors(UUID categoryId);

    List<CategoryNode> findDescendants(UUID categoryId);

    void moveCategory(UUID categoryId, UUID newParentId);

    void deleteById(UUID id);

    boolean existsByCode(String code);
}
