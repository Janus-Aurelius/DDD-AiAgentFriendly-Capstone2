package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.ProductRepository;
import com.retail.pim.catalog.infrastructure.persistence.entity.ProductJpaEntity;
import com.retail.pim.catalog.infrastructure.persistence.entity.VariantJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter: Implements the domain ProductRepository port for PostgreSQL persistence.
 * Performs deep mapping between Pure Java Domain Aggregates and JPA/JSONB Entities.
 */
@Repository
public class PostgresProductRepository implements ProductRepository {

    private final SpringDataProductFamilyJpaRepository jpaRepository;

    public PostgresProductRepository(SpringDataProductFamilyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProductFamily save(ProductFamily domain) {
        ProductJpaEntity entity = toJpaEntity(domain);
        ProductJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ProductFamily> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ProductFamily> findByCode(String code) {
        return jpaRepository.findByCode(code.toUpperCase()).map(this::toDomain);
    }

    @Override
    public Optional<ProductFamily> findBySku(String sku) {
        return jpaRepository.findBySku(sku).map(this::toDomain);
    }

    @Override
    public Optional<ProductFamily> findByVariantId(UUID variantId) {
        return jpaRepository.findByVariantId(variantId).map(this::toDomain);
    }

    @Override
    public List<ProductFamily> findByStatus(ProductLifecycleState status, int page, int size) {
        return jpaRepository.findByStatus(status, PageRequest.of(page, size))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductFamily> findAll(int page, int size) {
        return jpaRepository.findAll(PageRequest.of(page, size))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code.toUpperCase());
    }

    // --------------------------------------------------------------------------
    // DOMAIN <-> JPA ENTITY MAPPING
    // --------------------------------------------------------------------------

    private ProductJpaEntity toJpaEntity(ProductFamily domain) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setLocalizedDescriptions(domain.getLocalizedDescriptions());
        entity.setCategoryId(domain.getCategoryId());
        entity.setStatus(domain.getStatus());
        entity.setAttributes(domain.getAttributes());
        entity.setPublishedSnapshot(domain.getPublishedSnapshot());
        entity.setRevisionNumber(domain.getRevisionNumber());
        entity.setVersion(domain.getVersion());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt() : Instant.now());

        List<VariantJpaEntity> variantEntities = new ArrayList<>();
        for (Variant v : domain.getVariants()) {
            VariantJpaEntity ve = new VariantJpaEntity();
            ve.setId(v.getId());
            ve.setProductFamily(entity);
            ve.setSku(v.getSku());
            ve.setName(v.getName());
            ve.setPrice(v.getPrice());
            ve.setCurrency(v.getCurrency());
            ve.setDimensions(v.getDimensions());
            ve.setAttributes(v.getAttributes());
            ve.setDigitalAssets(v.getDigitalAssets());
            ve.setAvailabilityStatus(v.getAvailabilityStatus());
            ve.setActive(v.isActive());
            ve.setLifecycleState(v.getLifecycleState());
            ve.getActiveSuspensionReasons().addAll(v.getActiveSuspensionReasons());
            ve.getChannelStatusMap().putAll(v.getChannelStatusMap());
            ve.getMarketComplianceMap().putAll(v.getMarketComplianceMap());
            ve.getSuspendedComponents().addAll(v.getSuspendedComponents());
            ve.setEmbargoUntil(v.getEmbargoUntil());
            ve.setStreetDate(v.getStreetDate());
            ve.setBestseller(v.isBestseller());
            ve.setDisplayPriority(v.getDisplayPriority());
            ve.setPublishedVersion(v.getPublishedVersion());
            ve.setGtin(v.getGtin());
            ve.setFulfillmentMode(v.getFulfillmentMode());
            ve.setCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt() : Instant.now());
            ve.setUpdatedAt(v.getUpdatedAt() != null ? v.getUpdatedAt() : Instant.now());
            variantEntities.add(ve);
        }
        entity.setVariants(variantEntities);

        return entity;
    }

    private ProductFamily toDomain(ProductJpaEntity entity) {
        ProductFamily family = new ProductFamily(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getLocalizedDescriptions(),
                entity.getCategoryId(),
                entity.getStatus(),
                entity.getAttributes(),
                entity.getPublishedSnapshot(),
                entity.getRevisionNumber(),
                entity.getVersion()
        );

        if (entity.getVariants() != null) {
            for (VariantJpaEntity ve : entity.getVariants()) {
                Variant variant = new Variant(
                        ve.getId(),
                        ve.getSku(),
                        ve.getName(),
                        ve.getPrice(),
                        ve.getCurrency(),
                        ve.getDimensions(),
                        ve.getAttributes(),
                        ve.getDigitalAssets(),
                        ve.getAvailabilityStatus(),
                        ve.isActive()
                );
                if (ve.getLifecycleState() != null) {
                    variant.setLifecycleState(ve.getLifecycleState());
                }
                if (ve.getActiveSuspensionReasons() != null) {
                    ve.getActiveSuspensionReasons().forEach(variant::addSuspensionReason);
                }
                if (ve.getChannelStatusMap() != null) {
                    ve.getChannelStatusMap().forEach(variant::applyChannelStatus);
                }
                if (ve.getMarketComplianceMap() != null) {
                    ve.getMarketComplianceMap().forEach((reg, reason) -> variant.applyRegionalMask(reg, com.retail.pim.catalog.domain.enums.RestrictionReason.valueOf(reason)));
                }
                if (ve.getSuspendedComponents() != null) {
                    ve.getSuspendedComponents().forEach(variant::addSuspendedComponent);
                }
                variant.setEmbargoUntil(ve.getEmbargoUntil());
                variant.setStreetDate(ve.getStreetDate());
                variant.setBestseller(ve.isBestseller());
                variant.setDisplayPriority(ve.getDisplayPriority());
                variant.setPublishedVersion(ve.getPublishedVersion());
                variant.setGtin(ve.getGtin());
                variant.setFulfillmentMode(ve.getFulfillmentMode());
                family.addVariant(variant);
            }
        }

        // Reconstitution from persistence must clear initial creation domain events
        family.clearDomainEvents();
        return family;
    }

    public interface SpringDataProductFamilyJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {
        Optional<ProductJpaEntity> findByCode(String code);
        boolean existsByCode(String code);
        List<ProductJpaEntity> findByStatus(ProductLifecycleState status, org.springframework.data.domain.Pageable pageable);

        @Query("SELECT p FROM ProductJpaEntity p JOIN p.variants v WHERE v.sku = :sku")
        Optional<ProductJpaEntity> findBySku(@Param("sku") String sku);

        @Query("SELECT p FROM ProductJpaEntity p JOIN p.variants v WHERE v.id = :variantId")
        Optional<ProductJpaEntity> findByVariantId(@Param("variantId") UUID variantId);
    }
}
