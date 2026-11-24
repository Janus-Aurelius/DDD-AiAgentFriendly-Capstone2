package com.retail.pim.localization.domain.model;

import com.retail.pim.localization.domain.enums.RegionalStatus;
import com.retail.pim.localization.domain.events.RegionalItemPublished;
import com.retail.pim.localization.domain.valueobjects.TranslationBlock;
import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RegionalCatalogItem: The Aggregate Root for the Localization Context.
 * Governs regional attribute overrides, localized marketing translations, and country-specific compliance rules.
 *
 * Pure Java - zero framework annotations.
 */
public class RegionalCatalogItem extends AggregateRoot<UUID> {

    private final UUID globalProductId;
    private final String productCode;
    private final String regionCode; // US, CA, MX, etc.
    private RegionalStatus status;
    private final Map<String, RegionalOverride> overrides = new HashMap<>();
    private final Map<String, TranslationBlock> translations = new HashMap<>();
    private final Instant createdAt;
    private Instant updatedAt;

    public RegionalCatalogItem(UUID id, UUID globalProductId, String productCode, String regionCode) {
        super(id != null ? id : UUID.randomUUID());

        if (globalProductId == null) {
            throw new DomainException("Global product ID is required");
        }
        if (productCode == null || productCode.isBlank()) {
            throw new DomainException("Product code is required");
        }
        if (regionCode == null || regionCode.isBlank()) {
            throw new DomainException("Region code is required (e.g., US, CA, MX)");
        }

        this.globalProductId = globalProductId;
        this.productCode = productCode.trim().toUpperCase();
        this.regionCode = regionCode.trim().toUpperCase();
        this.status = RegionalStatus.PENDING_TRANSLATION;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void applyOverride(String key, String globalValue, String regionalValue, String reason) {
        RegionalOverride override = new RegionalOverride(UUID.randomUUID(), key, globalValue, regionalValue, reason);
        this.overrides.put(key.toLowerCase(), override);
        this.updatedAt = Instant.now();
    }

    public void addTranslationBlock(TranslationBlock block) {
        if (block == null) {
            throw new DomainException("TranslationBlock cannot be null");
        }
        this.translations.put(block.getLocale(), block);
        this.updatedAt = Instant.now();
    }

    /**
     * Dynamic Regional Rule Enforcement:
     * Validates whether translations and legal warnings satisfy the provided RegionRule.
     */
    public boolean evaluateReadiness(com.retail.pim.localization.domain.model.RegionRule rule) {
        boolean compliant;
        if (rule != null) {
            boolean hasAllLocales = rule.requiredLocales().isEmpty() ||
                    rule.requiredLocales().stream().allMatch(translations::containsKey);
            boolean satisfiesSafety = !rule.requiresSafetyWarnings() ||
                    translations.values().stream().anyMatch(TranslationBlock::hasSafetyWarnings);
            compliant = hasAllLocales && satisfiesSafety;
        } else {
            compliant = switch (this.regionCode) {
                case "CA" -> {
                    TranslationBlock fr = translations.get("fr_CA");
                    yield fr != null && fr.hasSafetyWarnings();
                }
                case "MX" -> translations.containsKey("es_MX");
                case "US" -> translations.containsKey("en_US");
                default -> !translations.isEmpty();
            };
        }

        if (compliant && this.status == RegionalStatus.PENDING_TRANSLATION) {
            this.status = RegionalStatus.READY_FOR_REGION;
            this.updatedAt = Instant.now();

            String primaryTitle = translations.values().stream()
                    .findFirst()
                    .map(TranslationBlock::getLocalizedTitle)
                    .orElse(this.productCode);

            Map<String, String> flatOverrides = this.overrides.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getRegionalValue()));

            registerEvent(new RegionalItemPublished(
                    this.getId(),
                    this.globalProductId,
                    this.productCode,
                    this.regionCode,
                    primaryTitle,
                    flatOverrides
            ));
            return true;
        }
        return false;
    }

    public boolean evaluateReadiness() {
        return evaluateReadiness(null);
    }

    public void forcePublish() {
        if (!evaluateReadiness()) {
            throw new DomainException(String.format(
                    "Cannot publish regional item [%s-%s]: Mandatory localized translations or safety warnings are missing",
                    this.productCode, this.regionCode));
        }
    }

    public UUID getGlobalProductId() {
        return globalProductId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public RegionalStatus getStatus() {
        return status;
    }

    public Map<String, RegionalOverride> getOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    public Map<String, TranslationBlock> getTranslations() {
        return Collections.unmodifiableMap(translations);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
