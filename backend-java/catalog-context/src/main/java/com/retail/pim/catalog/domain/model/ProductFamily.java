package com.retail.pim.catalog.domain.model;

import com.retail.pim.catalog.domain.compliance.CategoryCompliancePolicy;
import com.retail.pim.catalog.domain.compliance.ElectronicsCompliancePolicy;
import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.enums.VariantAvailabilityStatus;
import com.retail.pim.catalog.domain.enums.VariantLifecycleState;
import com.retail.pim.catalog.domain.events.ProductAttributesUpdatedEvent;
import com.retail.pim.catalog.domain.events.ProductPublished;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ProductFamily: The Core Catalog Aggregate Root.
 * Operates as an enterprise Finite State Machine governing the Product Lifecycle:
 * DRAFT -> ENRICHED -> APPROVED -> PUBLISHED, combined with FamilyLifecycleState (CUJ 6).
 *
 * Implements:
 * 1. Shadow Versioning: preserves live storefront publishedSnapshot during active draft revisions.
 * 2. Granular SKU Availability: supports partial publication of compliant variants.
 * 3. Decoupled Category Compliance Policies (Open-Closed Principle).
 * 4. Supplier Delta Merging: protects internal merchandising copy during vendor re-imports.
 * 5. "Last Variant Standing" & Concurrent Suspension Holds (CUJ 6).
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

    // Multi-Hold & Lifecycle State (CUJ 6)
    private final Set<String> activeSuspensionReasons = new HashSet<>();
    private FamilyLifecycleState lifecycleState = FamilyLifecycleState.FAMILY_DRAFT;

    // Shadow Versioning fields
    private Map<String, Object> publishedSnapshot;
    private int revisionNumber;

    private Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Full reconstitution constructor (used by repository persistence adapter).
     */
    public ProductFamily(UUID id, String code, String name, String description,
                         Map<String, String> localizedDescriptions, UUID categoryId,
                         ProductLifecycleState status, Map<String, AttributeValue> attributes,
                         Map<String, Object> publishedSnapshot, int revisionNumber,
                         Long version, Set<String> activeSuspensionReasons,
                         FamilyLifecycleState lifecycleState) {
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
        this.status = status != null ? status : ProductLifecycleState.DRAFT;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        this.publishedSnapshot = publishedSnapshot;
        this.revisionNumber = revisionNumber > 0 ? revisionNumber : 1;
        this.version = version != null ? version : 0L;
        if (activeSuspensionReasons != null) {
            this.activeSuspensionReasons.addAll(activeSuspensionReasons);
        }
        if (lifecycleState != null) {
            this.lifecycleState = lifecycleState;
        } else {
            this.lifecycleState = (this.status == ProductLifecycleState.PUBLISHED)
                    ? FamilyLifecycleState.FAMILY_PUBLISHED
                    : FamilyLifecycleState.FAMILY_DRAFT;
        }
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public ProductFamily(UUID id, String code, String name, String description,
                         Map<String, String> localizedDescriptions, UUID categoryId,
                         ProductLifecycleState status, Map<String, AttributeValue> attributes,
                         Map<String, Object> publishedSnapshot, int revisionNumber,
                         Long version) {
        this(id, code, name, description, localizedDescriptions, categoryId, status, attributes,
                publishedSnapshot, revisionNumber, version, null, null);
    }

    public ProductFamily(UUID id, String code, String name, String description,
                         Map<String, String> localizedDescriptions, UUID categoryId,
                         ProductLifecycleState status, Map<String, AttributeValue> attributes,
                         Long version) {
        this(id, code, name, description, localizedDescriptions, categoryId, status, attributes, null, 1, version);
    }

    /**
     * Factory method: Instantiates a new ProductFamily aggregate in DRAFT state.
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
                null,
                1,
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
     * Uses decoupled CategoryCompliancePolicy (Open-Closed Principle).
     * Supports Granular SKU Availability (partial publishing).
     */
    public void approve(CategoryCompliancePolicy compliancePolicy) {
        if (this.status != ProductLifecycleState.ENRICHED) {
            throw new DomainException(String.format(
                    "Cannot approve product '%s': Aggregate must be in ENRICHED state, but is currently '%s'",
                    this.code, this.status));
        }

        if (variants.isEmpty()) {
            throw new DomainException(String.format(
                    "Approval failed for product '%s': At least one physical SKU variant is required",
                    this.code));
        }

        // Evaluate SKU-level availability
        boolean atLeastOneSkuReady = false;
        for (Variant variant : variants.values()) {
            variant.evaluateAvailability();
            if (variant.hasWeightAndDimensions()) {
                atLeastOneSkuReady = true;
            }
        }

        if (!atLeastOneSkuReady) {
            throw new DomainException(String.format(
                    "Approval failed for product '%s': At least one variant SKU must have valid weight and dimensions",
                    this.code));
        }

        // Evaluate regulatory compliance via decoupled policy engine
        if (compliancePolicy != null) {
            compliancePolicy.validateCompliance(this);
        }

        this.status = ProductLifecycleState.APPROVED;
        this.updatedAt = Instant.now();
    }

    public void approve() {
        approve((CategoryCompliancePolicy) null);
    }

    /**
     * Transition 3 -> 4: APPROVED -> PUBLISHED
     * Employs Granular SKU Availability: Publishes all compliant SKUs while excluding SKU_INCOMPLETE ones.
     * Captures a live published snapshot for Shadow Versioning.
     */
    public void publish() {
        if (this.status != ProductLifecycleState.APPROVED) {
            throw new DomainException(String.format(
                    "Cannot publish product '%s': Aggregate must be in APPROVED state, but is currently '%s'",
                    this.code, this.status));
        }

        // Identify publishable SKUs (active, valid dimensions, primary image)
        List<String> publishableSkus = variants.values().stream()
                .filter(Variant::isPublishable)
                .map(Variant::getSku)
                .collect(Collectors.toList());

        if (publishableSkus.isEmpty()) {
            throw new DomainException(String.format(
                    "Publish failed for product '%s': None of the variants have both primary image and dimensions ready",
                    this.code));
        }

        this.status = ProductLifecycleState.PUBLISHED;
        this.lifecycleState = FamilyLifecycleState.FAMILY_PUBLISHED;
        this.updatedAt = Instant.now();

        // Capture live snapshot for shadow versioning
        this.publishedSnapshot = createSnapshot(publishableSkus);

        // Register Domain Event with publishable SKUs
        registerEvent(new ProductPublished(
                this.getId(),
                this.code,
                this.name,
                this.categoryId,
                publishableSkus
        ));
    }

    /**
     * Shadow Versioning: Starts an in-place draft revision without unpublishing live e-commerce listings.
     * The publishedSnapshot remains live and accessible while merchandising works on revisionNumber + 1.
     */
    public void startDraftRevision() {
        if (this.status != ProductLifecycleState.PUBLISHED) {
            throw new DomainException("Can only start a draft revision on an already PUBLISHED product");
        }
        this.revisionNumber++;
        this.status = ProductLifecycleState.DRAFT;
        this.updatedAt = Instant.now();
    }

    /**
     * Supplier Delta Merging: Merges technical supplier updates without overwriting
     * verified localized merchandising descriptions.
     */
    public void mergeSupplierDelta(Map<String, AttributeValue> supplierAttributes, String supplierDescription) {
        if (supplierAttributes != null && !supplierAttributes.isEmpty()) {
            this.attributes.putAll(supplierAttributes);
        }
        if (this.description == null || this.description.isBlank()) {
            this.description = supplierDescription;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * The Rule/Lock (Cross-Domain DAM Integration):
     * Removes expired asset ID. If any SKU loses its primary image, it is marked SKU_INCOMPLETE.
     * If no SKUs remain publishable, product drops back to ENRICHED.
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
            boolean anyPublishable = variants.values().stream().anyMatch(Variant::isPublishable);
            if (!anyPublishable && (this.status == ProductLifecycleState.PUBLISHED || this.status == ProductLifecycleState.APPROVED)) {
                this.status = ProductLifecycleState.ENRICHED;
            }
        }
        return anyRemoved;
    }

    public void returnToDraft(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("A valid reason must be supplied to return product to DRAFT");
        }
        this.status = ProductLifecycleState.DRAFT;
        this.updatedAt = Instant.now();
    }

    private Map<String, Object> createSnapshot(List<String> publishableSkus) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("code", this.code);
        snapshot.put("name", this.name);
        snapshot.put("description", this.description);
        snapshot.put("localizedDescriptions", new HashMap<>(this.localizedDescriptions));
        snapshot.put("publishedSkus", publishableSkus);
        snapshot.put("revisionNumber", this.revisionNumber);
        snapshot.put("snapshotTimestamp", Instant.now().toString());
        return snapshot;
    }

    // ==========================================================================
    // AGGREGATE MEMBER MANAGEMENT
    // ==========================================================================

    public void addVariant(Variant variant) {
        if (variant == null) {
            throw new DomainException("Variant cannot be null");
        }
        if (this.status == ProductLifecycleState.PUBLISHED && publishedSnapshot != null) {
            throw new DomainException("Cannot directly add variants to a live PUBLISHED product; call startDraftRevision() first");
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
        if (newAttributes != null && !newAttributes.isEmpty()) {
            this.attributes.putAll(newAttributes);
            this.updatedAt = Instant.now();
            registerEvent(new ProductAttributesUpdatedEvent(
                    this.getId(),
                    this.version,
                    this.categoryId,
                    this.attributes
            ));
        }
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

    public List<String> getPublishableVariantSkus() {
        return variants.values().stream()
                .filter(Variant::isPublishable)
                .map(Variant::getSku)
                .collect(Collectors.toList());
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

    /**
     * Resolves the primary "Hero Variant" for storefront display.
     * Evaluates regional availability masks (e.g. California Prop 65).
     * Deterministically falls back to the highest priority active compliant variant.
     */
    public Optional<Variant> resolveHeroVariant(String marketLocale) {
        return variants.values().stream()
                .filter(v -> v.getLifecycleState() == VariantLifecycleState.SKU_ACTIVE || v.isActive())
                .filter(v -> marketLocale == null || marketLocale.isBlank() || v.isRegionallyCompliant(marketLocale))
                .sorted(Comparator
                        .comparing(Variant::isBestseller).reversed()
                        .thenComparing(Comparator.comparingInt(Variant::getDisplayPriority).reversed())
                        .thenComparing(Variant::getSku))
                .findFirst();
    }

    public Map<String, Object> getPublishedSnapshot() {
        return publishedSnapshot != null ? Collections.unmodifiableMap(publishedSnapshot) : null;
    }

    public int getRevisionNumber() {
        return revisionNumber;
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

    // ==========================================================================
    // MULTI-HOLD CONCURRENCY & LAST VARIANT STANDING (CUJ 6)
    // ==========================================================================

    /**
     * Applies an administrative hold to the Product Family.
     * Transitions family to FAMILY_SUSPENDED while preserving existing holds in activeSuspensionReasons.
     */
    public void applyHold(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Suspension reason cannot be empty");
        }
        this.activeSuspensionReasons.add(reason.trim());
        if (this.lifecycleState == FamilyLifecycleState.FAMILY_PUBLISHED) {
            this.lifecycleState = FamilyLifecycleState.FAMILY_SUSPENDED;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Lifts an administrative hold from the Product Family.
     * Only restores to FAMILY_PUBLISHED if NO other suspension holds remain AND at least 1 active variant exists.
     */
    public void liftHold(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        this.activeSuspensionReasons.remove(reason.trim());
        if (this.activeSuspensionReasons.isEmpty() && countActiveVariants() > 0) {
            if (this.status == ProductLifecycleState.PUBLISHED || this.lifecycleState == FamilyLifecycleState.FAMILY_SUSPENDED) {
                this.lifecycleState = FamilyLifecycleState.FAMILY_PUBLISHED;
            }
        }
        this.updatedAt = Instant.now();
    }

    /**
     * The "Last Variant Standing" Rule:
     * When active variants drop to 0, automatically suspends family with reason NO_ACTIVE_VARIANTS.
     * When variants recover (> 0), clears NO_ACTIVE_VARIANTS while respecting any other concurrent holds (e.g. TRADEMARK_DISPUTE).
     */
    public void evaluateVariantCount(int activeCount) {
        if (activeCount == 0) {
            this.activeSuspensionReasons.add("NO_ACTIVE_VARIANTS");
            if (this.lifecycleState == FamilyLifecycleState.FAMILY_PUBLISHED) {
                this.lifecycleState = FamilyLifecycleState.FAMILY_SUSPENDED;
            }
        } else {
            this.activeSuspensionReasons.remove("NO_ACTIVE_VARIANTS");
            if (this.activeSuspensionReasons.isEmpty() &&
                (this.status == ProductLifecycleState.PUBLISHED || this.lifecycleState == FamilyLifecycleState.FAMILY_SUSPENDED)) {
                this.lifecycleState = FamilyLifecycleState.FAMILY_PUBLISHED;
            }
        }
        this.updatedAt = Instant.now();
    }

    public int countActiveVariants() {
        return (int) variants.values().stream()
                .filter(v -> v.getLifecycleState() == VariantLifecycleState.SKU_ACTIVE || v.isActive())
                .count();
    }

    public Set<String> getActiveSuspensionReasons() {
        return Collections.unmodifiableSet(activeSuspensionReasons);
    }

    public FamilyLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(FamilyLifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
        this.updatedAt = Instant.now();
    }
}
