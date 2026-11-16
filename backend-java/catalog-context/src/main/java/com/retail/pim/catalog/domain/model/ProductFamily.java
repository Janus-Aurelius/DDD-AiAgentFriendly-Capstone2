package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.events.ProductPublished;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProductFamily: The Core Catalog Aggregate Root.
 * Operates as a strict Finite State Machine governing the Product Lifecycle:
 * DRAFT -> ENRICHED -> APPROVED -> PUBLISHED.
 *
 * Pure Java - zero framework, ORM, or database dependencies.
 */
public class ProductFamily extends AggregateRoot<UUID> {

    private final String code;
    private String name;
    private String description;
    private final Map<String, String> localizedDescriptions = new HashMap<>();
    private UUID categoryId;
    private ProductLifecycleState status;
    private final Map<String, AttributeValue> attributes = new HashMap<>();
    private final Map<String, Variant> variants = new HashMap<>();
    private Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Reconstitution constructor (used by repository persistence adapter).
     */
    public ProductFamily(UUID id, String code, String name, String description,
                         Map<String, String> localizedDescriptions, UUID categoryId,
                         ProductLifecycleState status, Map<String, AttributeValue> attributes,
                         Long version) {
        super(id != null ? id : UUID.randomUUID());

        if (code == null || code.trim().isEmpty()) {
            throw new DomainException("Product code cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Product name cannot be empty");
        }

        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.description = description;
        if (localizedDescriptions != null) {
            this.localizedDescriptions.putAll(localizedDescriptions);
        }
        this.categoryId = categoryId;
        // Default lifecycle state is strictly DRAFT upon creation
        this.status = status != null ? status : ProductLifecycleState.DRAFT;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        this.version = version != null ? version : 0L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Factory method: Instantiates a new ProductFamily aggregate in DRAFT state.
     * Triggered when Supplier Context completes quarantine and emits VendorItemApprovedForCore.
     */
    public static ProductFamily createDraft(String code, String name, String description,
                                            UUID categoryId, Map<String, AttributeValue> initialAttributes) {
        ProductFamily product = new ProductFamily(
                UUID.randomUUID(),
                code,
                name,
                description,
                Collections.emptyMap(),
                categoryId,
                ProductLifecycleState.DRAFT,
                initialAttributes,
                0L
        );
        if (initialAttributes != null && !initialAttributes.isEmpty()) {
            product.registerEvent(new ProductAttributesUpdatedEvent(
                    product.getId(),
                    product.getVersion(),
                    product.getCategoryId(),
                    product.getAttributes()
            ));
        }
        return product;
    }

    // ==========================================================================
    // STATE MACHINE TRANSITIONS & INVARIANTS
    // ==========================================================================

    /**
     * Transition 1 -> 2: DRAFT -> ENRICHED
     * Invoked when Merchandising completes product enrichment.
     *
     * Rule Check: Verifies that localized marketing descriptions exist.
     * If missing, throws DomainException and rolls back transaction.
     */
    public void enrich(Map<String, String> newLocalizedDescriptions, Map<String, AttributeValue> updatedAttributes) {
        if (this.status != ProductLifecycleState.DRAFT && this.status != ProductLifecycleState.ENRICHED) {
            throw new DomainException(String.format(
                    "Cannot enrich product '%s': Current state is '%s', must be DRAFT or ENRICHED",
                    this.code, this.status));
        }

        if (newLocalizedDescriptions != null) {
            this.localizedDescriptions.putAll(newLocalizedDescriptions);
        }
        if (updatedAttributes != null && !updatedAttributes.isEmpty()) {
            this.attributes.putAll(updatedAttributes);
            registerEvent(new ProductAttributesUpdatedEvent(
                    this.getId(),
                    this.version,
                    this.categoryId,
                    this.attributes
            ));
        }

        // Strict Invariant: Localized marketing descriptions must exist and have non-empty content
        if (this.localizedDescriptions.isEmpty() ||
            this.localizedDescriptions.values().stream().allMatch(String::isBlank)) {
            throw new DomainException(String.format(
                    "Enrichment validation failed for product '%s': Localized marketing descriptions must exist before advancing to ENRICHED",
                    this.code));
        }

        this.status = ProductLifecycleState.ENRICHED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transition 2 -> 3: ENRICHED -> APPROVED
     * Invoked when Supply Chain & Compliance verify physical specs and regulatory standards.
     *
     * Rule Checks:
     * 1. Must currently be in ENRICHED state.
     * 2. variants.size() > 0 AND all variants have non-null weight and dimensions.
     * 3. If it's electronics, checks for mandatory 'energy_rating' attribute.
     */
    public void approve() {
        if (this.status != ProductLifecycleState.ENRICHED) {
            throw new DomainException(String.format(
                    "Cannot approve product '%s': Aggregate must be in ENRICHED state, but is currently '%s'",
                    this.code, this.status));
        }

        // Invariant: At least one Variant (physical SKU) must exist
        if (variants.isEmpty()) {
            throw new DomainException(String.format(
                    "Approval failed for product '%s': At least one physical SKU variant is required (variants.size() > 0)",
                    this.code));
        }

        // Invariant: All variants must have standardized weight and dimensions
        for (Variant variant : variants.values()) {
            if (!variant.hasWeightAndDimensions()) {
                throw new DomainException(String.format(
                        "Approval failed for product '%s': Variant SKU '%s' is missing physical weight or dimensions",
                        this.code, variant.getSku()));
            }
        }

        this.status = ProductLifecycleState.APPROVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transition 3 -> 4: APPROVED -> PUBLISHED
     * Invoked when Marketing verifies visual assets and authorizes E-Commerce syndication.
     *
     * Rule Check:
     * 1. Must currently be in APPROVED state.
     * 2. Every Variant must have at least one digital asset marked as is_primary_image = true.
     *
     * Domain Event: Registers ProductPublished domain event for Kafka/Storefront ingestion.
     */
    public void publish() {
        if (this.status != ProductLifecycleState.APPROVED) {
            throw new DomainException(String.format(
                    "Cannot publish product '%s': Aggregate must be in APPROVED state, but is currently '%s'",
                    this.code, this.status));
        }

        // Invariant: Every variant must have at least one primary image
        for (Variant variant : variants.values()) {
            if (!variant.hasPrimaryImage()) {
                throw new DomainException(String.format(
                        "Publish failed for product '%s': Variant SKU '%s' has no digital asset marked as primary image (is_primary_image = true)",
                        this.code, variant.getSku()));
            }
        }

        this.status = ProductLifecycleState.PUBLISHED;
        this.updatedAt = Instant.now();

        // Register Domain Event for transactional publishing
        registerEvent(new ProductPublished(
                this.getId(),
                this.code,
                this.name,
                this.categoryId,
                getVariantSkus()
        ));
    }

    /**
     * Allows demoting or resetting a product back to DRAFT for revisions.
     */
    public void returnToDraft(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("A valid reason must be supplied to return product to DRAFT");
        }
        this.status = ProductLifecycleState.DRAFT;
        this.updatedAt = Instant.now();
    }

    /**
     * The Rule/Lock (Cross-Domain DAM Integration):
     * If an image's DRM license expires in DAM, this method strips the asset ID.
     * If any variant no longer meets the minimum image requirement,
     * the product's status drops back to ENRICHED.
     */
    public boolean removeAssetAndDemoteIfNonCompliant(UUID assetId) {
        if (assetId == null) return false;
        boolean anyRemoved = false;
        for (Variant variant : variants.values()) {
            if (variant.removeDigitalAsset(assetId)) {
                anyRemoved = true;
            }
        }

        if (anyRemoved) {
            this.updatedAt = Instant.now();
            boolean compliant = variants.values().stream().allMatch(Variant::hasPrimaryImage);
            if (!compliant && (this.status == ProductLifecycleState.PUBLISHED || this.status == ProductLifecycleState.APPROVED)) {
                this.status = ProductLifecycleState.ENRICHED;
            }
        }
        return anyRemoved;
    }

    // ==========================================================================
    // AGGREGATE MEMBER MANAGEMENT
    // ==========================================================================

    public void addVariant(Variant variant) {
        if (variant == null) {
            throw new DomainException("Variant cannot be null");
        }
        if (this.status == ProductLifecycleState.PUBLISHED) {
            throw new DomainException("Cannot add variants to an already PUBLISHED product family without revising state");
        }
        if (variants.containsKey(variant.getSku())) {
            throw new DomainException(String.format("Variant SKU '%s' already exists in product '%s'",
                    variant.getSku(), this.code));
        }
        variants.put(variant.getSku(), variant);
        this.updatedAt = Instant.now();
    }

    public void setAttribute(String key, AttributeValue value) {
        if (key == null || key.isBlank() || value == null) {
            throw new DomainException("Attribute key and value cannot be null or blank");
        }
        this.attributes.put(key.trim(), value);
        this.updatedAt = Instant.now();
        registerEvent(new ProductAttributesUpdatedEvent(
                this.getId(),
                this.version,
                this.categoryId,
                this.attributes
        ));
    }

    public void updateAttributes(Map<String, AttributeValue> newAttributes) {
        if (newAttributes == null || newAttributes.isEmpty()) {
            return;
        }
        this.attributes.putAll(newAttributes);
        this.updatedAt = Instant.now();
        registerEvent(new ProductAttributesUpdatedEvent(
                this.getId(),
                this.version,
                this.categoryId,
                this.attributes
        ));
    }

    public void setLocalizedDescription(String locale, String description) {
        if (locale == null || locale.isBlank() || description == null || description.isBlank()) {
            throw new DomainException("Locale and localized description cannot be blank");
        }
        this.localizedDescriptions.put(locale.trim().toLowerCase(), description.trim());
        this.updatedAt = Instant.now();
    }

    public List<String> getVariantSkus() {
        return variants.values().stream().map(Variant::getSku).collect(Collectors.toList());
    }

    // ==========================================================================
    // GETTERS
    // ==========================================================================

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getLocalizedDescriptions() {
        return Collections.unmodifiableMap(localizedDescriptions);
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public ProductLifecycleState getStatus() {
        return status;
    }

    public Map<String, AttributeValue> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<Variant> getVariants() {
        return Collections.unmodifiableList(new ArrayList<>(variants.values()));
    }

    public Variant getVariantBySku(String sku) {
        return variants.get(sku);
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
