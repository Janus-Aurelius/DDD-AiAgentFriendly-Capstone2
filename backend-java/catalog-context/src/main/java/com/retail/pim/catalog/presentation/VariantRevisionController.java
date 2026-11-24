package com.retail.pim.catalog.presentation;

import com.retail.pim.catalog.application.services.VariantRevisionUseCase;
import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.model.VariantRevision;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing Shadow Working Revisions for variant remediation without downtime.
 */
@RestController
@RequestMapping("/api/v1/catalog/variants/{sku}/revisions")
public class VariantRevisionController {

    private final VariantRevisionUseCase revisionService;

    public VariantRevisionController(VariantRevisionUseCase revisionService) {
        this.revisionService = revisionService;
    }

    public record UpdateRevisionRequestDto(
            Map<String, Object> attributes
    ) {}

    public record RevisionResponseDto(
            UUID id,
            UUID variantId,
            long baseVersion,
            RevisionState state,
            Map<String, Object> remediatedAttributes,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RevisionResponseDto fromDomain(VariantRevision revision) {
            Map<String, Object> attrMap = new HashMap<>();
            revision.getRemediatedAttributes().forEach((key, val) -> {
                if (val != null) {
                    attrMap.put(key, val.getValue());
                }
            });
            return new RevisionResponseDto(
                    revision.getId(),
                    revision.getVariantId(),
                    revision.getBaseVersion(),
                    revision.getRevisionState(),
                    attrMap,
                    revision.getCreatedAt(),
                    revision.getUpdatedAt()
            );
        }
    }

    @PostMapping
    public ResponseEntity<RevisionResponseDto> createWorkingRevision(@PathVariable String sku) {
        VariantRevision revision = revisionService.createWorkingRevision(sku);
        return ResponseEntity.status(HttpStatus.CREATED).body(RevisionResponseDto.fromDomain(revision));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RevisionResponseDto> getRevision(@PathVariable String sku, @PathVariable UUID id) {
        VariantRevision revision = revisionService.getRevision(id);
        return ResponseEntity.ok(RevisionResponseDto.fromDomain(revision));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RevisionResponseDto> updateWorkingRevision(
            @PathVariable String sku,
            @PathVariable UUID id,
            @RequestBody UpdateRevisionRequestDto request) {
        Map<String, AttributeValue> domainAttrs = new HashMap<>();
        if (request.attributes() != null) {
            request.attributes().forEach((k, v) -> {
                if (v instanceof Boolean b) {
                    domainAttrs.put(k, AttributeValue.ofBoolean(b));
                } else if (v instanceof Number n) {
                    domainAttrs.put(k, AttributeValue.ofNumber(n, null));
                } else if (v != null) {
                    domainAttrs.put(k, AttributeValue.ofString(v.toString()));
                }
            });
        }
        VariantRevision updated = revisionService.updateWorkingRevision(id, domainAttrs);
        return ResponseEntity.ok(RevisionResponseDto.fromDomain(updated));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<RevisionResponseDto> submitForReview(
            @PathVariable String sku,
            @PathVariable UUID id) {
        VariantRevision submitted = revisionService.submitForReview(id);
        return ResponseEntity.ok(RevisionResponseDto.fromDomain(submitted));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<RevisionResponseDto> promoteRevision(
            @PathVariable String sku,
            @PathVariable UUID id) {
        VariantRevision promoted = revisionService.promoteRevision(id);
        return ResponseEntity.ok(RevisionResponseDto.fromDomain(promoted));
    }
}
