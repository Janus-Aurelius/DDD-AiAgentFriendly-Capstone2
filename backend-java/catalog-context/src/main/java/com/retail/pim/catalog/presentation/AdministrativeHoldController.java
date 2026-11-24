package com.retail.pim.catalog.presentation;

import com.retail.pim.catalog.application.services.FamilyHoldManagementService;
import com.retail.pim.catalog.application.services.FamilyHoldUseCase;
import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * REST Controller for managing Administrative and Legal Holds on Product Families (CUJ 6).
 */
@RestController
@RequestMapping("/api/v1/catalog/families/{id}/holds")
public class AdministrativeHoldController {

    private final FamilyHoldUseCase holdManagementService;

    public AdministrativeHoldController(FamilyHoldUseCase holdManagementService) {
        this.holdManagementService = holdManagementService;
    }

    public record HoldRequestDto(String reason) {}

    public record FamilyHoldResponseDto(
            UUID familyId,
            String code,
            FamilyLifecycleState lifecycleState,
            Set<String> activeSuspensionReasons,
            Long version
    ) {
        public static FamilyHoldResponseDto fromDomain(ProductFamily family) {
            return new FamilyHoldResponseDto(
                    family.getId(),
                    family.getCode(),
                    family.getLifecycleState(),
                    family.getActiveSuspensionReasons(),
                    family.getVersion()
            );
        }
    }

    @PostMapping
    public ResponseEntity<FamilyHoldResponseDto> applyHold(
            @PathVariable UUID id,
            @RequestBody HoldRequestDto request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long expectedVersion = parseVersionHeader(ifMatch);
        ProductFamily updatedFamily = holdManagementService.applyHold(id, request.reason(), expectedVersion);
        return ResponseEntity.ok(FamilyHoldResponseDto.fromDomain(updatedFamily));
    }

    @DeleteMapping("/{reason}")
    public ResponseEntity<FamilyHoldResponseDto> liftHold(
            @PathVariable UUID id,
            @PathVariable String reason,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long expectedVersion = parseVersionHeader(ifMatch);
        ProductFamily updatedFamily = holdManagementService.liftHold(id, reason, expectedVersion);
        return ResponseEntity.ok(FamilyHoldResponseDto.fromDomain(updatedFamily));
    }

    @GetMapping
    public ResponseEntity<Set<String>> getActiveHolds(@PathVariable UUID id) {
        Set<String> holds = holdManagementService.getActiveHolds(id);
        return ResponseEntity.ok(holds);
    }

    private Long parseVersionHeader(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String clean = ifMatch.replace("\"", "").trim();
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
