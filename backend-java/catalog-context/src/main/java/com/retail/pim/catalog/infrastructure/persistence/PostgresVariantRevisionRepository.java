package com.retail.pim.catalog.infrastructure.persistence;

import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.model.VariantRevision;
import com.retail.pim.catalog.domain.repository.VariantRevisionRepository;
import com.retail.pim.catalog.infrastructure.persistence.entity.VariantRevisionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresVariantRevisionRepository implements VariantRevisionRepository {

    private final SpringDataVariantRevisionRepository springDataRepo;

    public PostgresVariantRevisionRepository(SpringDataVariantRevisionRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public VariantRevision save(VariantRevision domain) {
        VariantRevisionJpaEntity entity = toEntity(domain);
        VariantRevisionJpaEntity saved = springDataRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<VariantRevision> findById(UUID id) {
        return springDataRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<VariantRevision> findByVariantId(UUID variantId) {
        return springDataRepo.findByVariantId(variantId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<VariantRevision> findActiveDraftByVariantId(UUID variantId) {
        List<VariantRevisionJpaEntity> entities = springDataRepo.findByVariantIdAndState(variantId, RevisionState.WORKING_DRAFT);
        return entities.isEmpty() ? Optional.empty() : Optional.of(toDomain(entities.get(0)));
    }

    private VariantRevisionJpaEntity toEntity(VariantRevision domain) {
        return new VariantRevisionJpaEntity(
                domain.getId(),
                domain.getVariantId(),
                domain.getBaseVersion(),
                domain.getRevisionState(),
                domain.getRemediatedAttributes(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    private VariantRevision toDomain(VariantRevisionJpaEntity entity) {
        return new VariantRevision(
                entity.getId(),
                entity.getVariantId(),
                entity.getBaseVersion(),
                entity.getRevisionState(),
                entity.getRemediatedAttributes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
