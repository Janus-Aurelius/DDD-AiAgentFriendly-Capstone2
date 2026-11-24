package com.retail.pim.dam.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.dam.domain.model.DigitalAsset;
import com.retail.pim.dam.domain.model.Rendition;
import com.retail.pim.dam.domain.repository.DigitalAssetRepository;
import com.retail.pim.dam.domain.valueobjects.AssetMetadata;
import com.retail.pim.dam.domain.valueobjects.DRMLicense;
import com.retail.pim.dam.infrastructure.persistence.entity.DigitalAssetJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter: Implements DigitalAssetRepository using Spring Data JPA and PostgreSQL JSONB.
 */
@Repository
@Primary
public class PostgresDigitalAssetRepository implements DigitalAssetRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresDigitalAssetRepository.class);

    private final SpringDataDigitalAssetRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public PostgresDigitalAssetRepository(SpringDataDigitalAssetRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DigitalAsset save(DigitalAsset asset) {
        DigitalAssetJpaEntity entity = toJpaEntity(asset);
        jpaRepository.save(entity);
        return asset;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DigitalAsset> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DigitalAsset> findActiveAssetsWithLicenses() {
        return jpaRepository.findByActiveTrue().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DigitalAsset> findByProductId(UUID productId) {
        String jsonMatch = "[\"" + productId + "\"]";
        return jpaRepository.findByProductIdJson(jsonMatch).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DigitalAssetJpaEntity toJpaEntity(DigitalAsset domain) {
        try {
            String metadataJson = domain.getMetadata() != null ? objectMapper.writeValueAsString(domain.getMetadata()) : null;
            String licenseJson = domain.getLicense() != null ? objectMapper.writeValueAsString(domain.getLicense()) : "{}";
            String renditionsJson = objectMapper.writeValueAsString(domain.getRenditions());
            String linkedProductsJson = objectMapper.writeValueAsString(domain.getLinkedProductIds());

            return new DigitalAssetJpaEntity(
                    domain.getId(),
                    domain.getOriginalFileName(),
                    domain.getTitle(),
                    domain.getRawS3Url(),
                    metadataJson,
                    licenseJson,
                    renditionsJson,
                    linkedProductsJson,
                    domain.isActive(),
                    domain.getCreatedAt(),
                    domain.getUpdatedAt()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to map DigitalAsset to JPA entity", e);
            throw new RuntimeException("JSON serialization error persisting DigitalAsset", e);
        }
    }

    private DigitalAsset toDomain(DigitalAssetJpaEntity entity) {
        try {
            AssetMetadata metadata = entity.getMetadataJson() != null
                    ? objectMapper.readValue(entity.getMetadataJson(), AssetMetadata.class)
                    : null;
            DRMLicense license = entity.getLicenseJson() != null
                    ? objectMapper.readValue(entity.getLicenseJson(), DRMLicense.class)
                    : null;
            List<Rendition> renditions = entity.getRenditionsJson() != null
                    ? objectMapper.readValue(entity.getRenditionsJson(), new TypeReference<List<Rendition>>() {})
                    : Collections.emptyList();
            Set<UUID> linkedProductIds = entity.getLinkedProductIdsJson() != null
                    ? objectMapper.readValue(entity.getLinkedProductIdsJson(), new TypeReference<Set<UUID>>() {})
                    : Collections.emptySet();

            DigitalAsset domain = new DigitalAsset(
                    entity.getId(),
                    entity.getOriginalFileName(),
                    entity.getTitle(),
                    entity.getRawS3Url(),
                    metadata,
                    license,
                    entity.isActive()
            );

            if (metadata != null) {
                domain.setMetadata(metadata);
            }
            for (Rendition rendition : renditions) {
                domain.addRendition(rendition);
            }
            for (UUID prodId : linkedProductIds) {
                domain.linkToProduct(prodId, "SKU-LINKED", false);
            }
            domain.clearDomainEvents(); // Clear events loaded from DB

            return domain;
        } catch (JsonProcessingException e) {
            log.error("Failed to map DigitalAssetJpaEntity to domain", e);
            throw new RuntimeException("JSON deserialization error reconstituting DigitalAsset", e);
        }
    }
}
