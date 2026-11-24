package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.model.BundleComponent;
import com.retail.pim.catalog.domain.repository.BundleComponentRepository;
import com.retail.pim.catalog.infrastructure.persistence.entity.BundleComponentJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class PostgresBundleComponentRepository implements BundleComponentRepository {

    private final SpringDataBundleComponentJpaRepository jpaRepository;

    public PostgresBundleComponentRepository(SpringDataBundleComponentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public BundleComponent save(BundleComponent domain) {
        BundleComponentJpaEntity entity = toEntity(domain);
        BundleComponentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<BundleComponent> saveAll(List<BundleComponent> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        List<BundleComponentJpaEntity> entities = components.stream().map(this::toEntity).collect(Collectors.toList());
        return jpaRepository.saveAll(entities).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UUID> findBundleVariantIdsByComponentSku(String componentSku) {
        return jpaRepository.findBundleVariantIdsByComponentSku(componentSku);
    }

    @Override
    public List<String> findComponentSkusByBundleVariantId(UUID bundleVariantId) {
        return jpaRepository.findComponentSkusByBundleVariantId(bundleVariantId);
    }

    @Override
    public List<BundleComponent> findByBundleVariantId(UUID bundleVariantId) {
        return jpaRepository.findByBundleVariantId(bundleVariantId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByBundleVariantId(UUID bundleVariantId) {
        jpaRepository.deleteByBundleVariantId(bundleVariantId);
    }

    private BundleComponentJpaEntity toEntity(BundleComponent domain) {
        return new BundleComponentJpaEntity(
                domain.getId(),
                domain.getBundleVariantId(),
                domain.getComponentSku(),
                domain.getRequiredQty(),
                domain.getCreatedAt()
        );
    }

    private BundleComponent toDomain(BundleComponentJpaEntity entity) {
        return new BundleComponent(
                entity.getId(),
                entity.getBundleVariantId(),
                entity.getComponentSku(),
                entity.getRequiredQty(),
                entity.getCreatedAt()
        );
    }
}
