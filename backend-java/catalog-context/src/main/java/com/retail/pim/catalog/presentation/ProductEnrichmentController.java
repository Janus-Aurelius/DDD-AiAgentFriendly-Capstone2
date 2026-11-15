package com.retail.pim.catalog.presentation;

import com.retail.pim.catalog.application.commands.CreateDraftProductCommand;
import com.retail.pim.catalog.application.commands.PublishProductCommand;
import com.retail.pim.catalog.application.services.ProductLifecycleService;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Presentation Adapter: Endpoints invoked by the Merchandising, Supply Chain,
 * and Marketing UI portals to manage the product lifecycle state machine.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductEnrichmentController {

    private final ProductLifecycleService lifecycleService;

    public ProductEnrichmentController(ProductLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    // --------------------------------------------------------------------------
    // DTOs
    // --------------------------------------------------------------------------

    public record CreateDraftRequest(
            String code,
            String name,
            String description,
            UUID categoryId,
            Map<String, String> initialAttributes
    ) {}

    public record EnrichRequest(
            Map<String, String> localizedDescriptions,
            Map<String, String> attributes
    ) {}

    public record AddVariantRequest(
            String sku,
            String name,
            BigDecimal price,
            String currency,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            BigDecimal weight,
            String unit,
            List<DigitalAssetDto> digitalAssets
    ) {}

    public record DigitalAssetDto(
            String url,
            String type,
            boolean isPrimaryImage
    ) {}

    public record PublishRequest(
            String requestedBy
    ) {}

    public record ProductResponse(
            UUID id,
            String code,
            String name,
            String description,
            Map<String, String> localizedDescriptions,
            UUID categoryId,
            ProductLifecycleState status,
            Map<String, String> attributes,
            List<VariantResponse> variants,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record VariantResponse(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            String currency,
            Dimensions dimensions,
            List<DigitalAsset> digitalAssets,
            boolean active
    ) {}

    // --------------------------------------------------------------------------
    // ENDPOINTS FOR MERCHANDISING LIFECYCLE
    // --------------------------------------------------------------------------

    /**
     * Endpoint to create a draft product manually
     */
    @PostMapping("/draft")
    public ResponseEntity<ProductResponse> createDraft(@RequestBody CreateDraftRequest request) {
        Map<String, AttributeValue> attrs = new HashMap<>();
        if (request.initialAttributes() != null) {
            request.initialAttributes().forEach((k, v) -> attrs.put(k, AttributeValue.ofString(v)));
        }

        CreateDraftProductCommand command = new CreateDraftProductCommand(
                request.code(),
                request.name(),
                request.description(),
                request.categoryId(),
                attrs,
                null
        );

        ProductFamily draft = lifecycleService.createDraftProduct(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(draft));
    }

    /**
     * State 1 -> 2: Merchandising submits enrichment.
     * Transitions state from DRAFT -> ENRICHED.
     */
    @PostMapping("/{id}/enrich")
    public ResponseEntity<ProductResponse> submitEnrichment(
            @PathVariable UUID id,
            @RequestBody EnrichRequest request) {

        Map<String, AttributeValue> attrs = new HashMap<>();
        if (request.attributes() != null) {
            request.attributes().forEach((k, v) -> attrs.put(k, AttributeValue.ofString(v)));
        }

        ProductFamily enriched = lifecycleService.enrichProduct(id, request.localizedDescriptions(), attrs);
        return ResponseEntity.ok(toResponse(enriched));
    }

    /**
     * State 2 -> 3: Supply chain & compliance approval.
     * Transitions state from ENRICHED -> APPROVED.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ProductResponse> approveProduct(@PathVariable UUID id) {
        ProductFamily approved = lifecycleService.approveProduct(id);
        return ResponseEntity.ok(toResponse(approved));
    }

    /**
     * State 3 -> 4: Marketing publishes product to e-commerce.
     * Transitions state from APPROVED -> PUBLISHED.
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<ProductResponse> publishProduct(
            @PathVariable UUID id,
            @RequestBody(required = false) PublishRequest request) {

        String requestedBy = request != null && request.requestedBy() != null ? request.requestedBy() : "merchandiser-ui";
        PublishProductCommand command = new PublishProductCommand(id, requestedBy);

        ProductFamily published = lifecycleService.publishProduct(command);
        return ResponseEntity.ok(toResponse(published));
    }

    /**
     * Attaches a Variant (SKU) with physical dimensions and digital assets.
     */
    @PostMapping("/{id}/variants")
    public ResponseEntity<ProductResponse> addVariant(
            @PathVariable UUID id,
            @RequestBody AddVariantRequest request) {

        Dimensions dims = null;
        if (request.length() != null && request.width() != null &&
            request.height() != null && request.weight() != null) {
            dims = new Dimensions(request.length(), request.width(), request.height(), request.weight(),
                    request.unit() != null ? request.unit() : "cm/kg");
        }

        List<DigitalAsset> assets = List.of();
        if (request.digitalAssets() != null) {
            assets = request.digitalAssets().stream()
                    .map(a -> new DigitalAsset(
                            UUID.randomUUID(),
                            a.url(),
                            a.type() != null ? DigitalAsset.AssetType.valueOf(a.type().toUpperCase()) : DigitalAsset.AssetType.IMAGE,
                            a.isPrimaryImage()
                    ))
                    .collect(Collectors.toList());
        }

        Variant variant = new Variant(
                UUID.randomUUID(),
                request.sku(),
                request.name(),
                request.price(),
                request.currency(),
                dims,
                Map.of(),
                assets,
                true
        );

        ProductFamily updated = lifecycleService.addVariant(id, variant);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        ProductFamily product = lifecycleService.getProductById(id);
        return ResponseEntity.ok(toResponse(product));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ProductFamily> products = lifecycleService.listProducts(page, size);
        return ResponseEntity.ok(products.stream().map(this::toResponse).toList());
    }

    // --------------------------------------------------------------------------
    // MAPPERS
    // --------------------------------------------------------------------------

    private ProductResponse toResponse(ProductFamily domain) {
        Map<String, String> flatAttrs = new HashMap<>();
        domain.getAttributes().forEach((k, v) -> flatAttrs.put(k, v.asString()));

        List<VariantResponse> variantResponses = domain.getVariants().stream()
                .map(v -> new VariantResponse(
                        v.getId(),
                        v.getSku(),
                        v.getName(),
                        v.getPrice(),
                        v.getCurrency(),
                        v.getDimensions(),
                        v.getDigitalAssets(),
                        v.isActive()
                ))
                .toList();

        return new ProductResponse(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getDescription(),
                domain.getLocalizedDescriptions(),
                domain.getCategoryId(),
                domain.getStatus(),
                flatAttrs,
                variantResponses,
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
