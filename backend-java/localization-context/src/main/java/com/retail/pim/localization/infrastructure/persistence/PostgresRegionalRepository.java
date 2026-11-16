package com.retail.pim.localization.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.localization.domain.enums.RegionalStatus;
import com.retail.pim.localization.domain.model.RegionalCatalogItem;
import com.retail.pim.localization.domain.model.RegionalOverride;
import com.retail.pim.localization.domain.repository.RegionalCatalogRepository;
import com.retail.pim.localization.domain.valueobjects.TranslationBlock;
import com.retail.pim.localization.infrastructure.persistence.entity.RegionalCatalogJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: Implements RegionalCatalogRepository using Spring Data JPA and PostgreSQL JSONB.
 */
@Repository
@Primary
public class PostgresRegionalRepository implements RegionalCatalogRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresRegionalRepository.class);

    private final SpringDataRegionalCatalogRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public PostgresRegionalRepository(SpringDataRegionalCatalogRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RegionalCatalogItem save(RegionalCatalogItem item) {
        RegionalCatalogJpaEntity entity = toJpaEntity(item);
        jpaRepository.save(entity);
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegionalCatalogItem> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegionalCatalogItem> findByGlobalProductAndRegion(UUID globalProductId, String regionCode) {
        return jpaRepository.findByGlobalProductIdAndRegionCode(globalProductId, regionCode.toUpperCase())
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionalCatalogItem> findByRegion(String regionCode) {
        return jpaRepository.findByRegionCode(regionCode.toUpperCase()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private RegionalCatalogJpaEntity toJpaEntity(RegionalCatalogItem domain) {
        try {
            String overridesJson = objectMapper.writeValueAsString(domain.getOverrides());
            String translationsJson = objectMapper.writeValueAsString(domain.getTranslations());

            return new RegionalCatalogJpaEntity(
                    domain.getId(),
                    domain.getGlobalProductId(),
                    domain.getProductCode(),
                    domain.getRegionCode(),
                    domain.getStatus().name(),
                    overridesJson,
                    translationsJson,
                    domain.getCreatedAt(),
                    domain.getUpdatedAt()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to map RegionalCatalogItem to JPA entity", e);
            throw new RuntimeException("JSON serialization error persisting RegionalCatalogItem", e);
        }
    }

    private RegionalCatalogItem toDomain(RegionalCatalogJpaEntity entity) {
        try {
            Map<String, RegionalOverride> overrides = entity.getOverridesJson() != null
                    ? objectMapper.readValue(entity.getOverridesJson(), new TypeReference<Map<String, RegionalOverride>>() {})
                    : Collections.emptyMap();

            Map<String, TranslationBlock> translations = entity.getTranslationsJson() != null
                    ? objectMapper.readValue(entity.getTranslationsJson(), new TypeReference<Map<String, TranslationBlock>>() {})
                    : Collections.emptyMap();

            RegionalCatalogItem domain = new RegionalCatalogItem(
                    entity.getId(),
                    entity.getGlobalProductId(),
                    entity.getProductCode(),
                    entity.getRegionCode()
            );

            // Reconstitute internal state
            for (RegionalOverride override : overrides.values()) {
                domain.applyOverride(override.getAttributeKey(), override.getGlobalDefaultValue(),
                        override.getRegionalValue(), override.getReason());
            }

            for (TranslationBlock block : translations.values()) {
                domain.addTranslationBlock(block);
            }

            domain.clearEvents(); // Clear reconstituted domain events
            return domain;
        } catch (JsonProcessingException e) {
            log.error("Failed to map RegionalCatalogJpaEntity to domain", e);
            throw new RuntimeException("JSON deserialization error reconstituting RegionalCatalogItem", e);
        }
    }
}
