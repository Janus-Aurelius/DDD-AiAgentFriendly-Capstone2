package com.retail.pim.catalog.presentation;

import com.retail.pim.catalog.application.services.FinancialMasterDataUseCase;
import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * REST Controller for querying and updating bi-temporal financial and logistics attributes (CUJ 7).
 */
@RestController
@RequestMapping("/api/v1/catalog/skus/{sku}/financials")
public class FinancialMasterDataController {

    private final FinancialMasterDataUseCase financialMasterDataUseCase;

    public FinancialMasterDataController(FinancialMasterDataUseCase financialMasterDataUseCase) {
        this.financialMasterDataUseCase = financialMasterDataUseCase;
    }

    public record FinancialAttributesDto(
            UUID id,
            String sku,
            String uomTier,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal grossWeightKg,
            String taxBand,
            String customsHsnCode,
            Instant effectiveFrom,
            Instant effectiveTo
    ) {
        public static FinancialAttributesDto fromDomain(FinancialAttributeHistory history) {
            return new FinancialAttributesDto(
                    history.getId(),
                    history.getSku(),
                    history.getUomTier(),
                    history.getLengthCm(),
                    history.getWidthCm(),
                    history.getHeightCm(),
                    history.getGrossWeightKg(),
                    history.getTaxBand(),
                    history.getCustomsHsnCode(),
                    history.getEffectiveFrom(),
                    history.getEffectiveTo()
            );
        }
    }

    public record RecordFinancialAttributesRequest(
            String uomTier,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal grossWeightKg,
            String taxBand,
            String customsHsnCode,
            Instant effectiveFrom
    ) {}

    @GetMapping
    public ResponseEntity<FinancialAttributesDto> getHistoricalSnapshot(
            @PathVariable String sku,
            @RequestParam(defaultValue = "EACH") String uomTier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {

        return financialMasterDataUseCase.getHistoricalSnapshot(sku, uomTier, asOf)
                .map(FinancialAttributesDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<FinancialAttributesDto> updateFinancialAttributes(
            @PathVariable String sku,
            @RequestBody RecordFinancialAttributesRequest request) {

        FinancialAttributeHistory updated = financialMasterDataUseCase.recordFinancialAttributes(
                sku,
                request.uomTier() != null ? request.uomTier() : "EACH",
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                request.grossWeightKg(),
                request.taxBand(),
                request.customsHsnCode(),
                request.effectiveFrom() != null ? request.effectiveFrom() : Instant.now()
        );

        return ResponseEntity.ok(FinancialAttributesDto.fromDomain(updated));
    }

    @PostMapping
    public ResponseEntity<FinancialAttributesDto> createFinancialAttributes(
            @PathVariable String sku,
            @RequestBody RecordFinancialAttributesRequest request) {

        FinancialAttributeHistory created = financialMasterDataUseCase.recordFinancialAttributes(
                sku,
                request.uomTier() != null ? request.uomTier() : "EACH",
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                request.grossWeightKg(),
                request.taxBand(),
                request.customsHsnCode(),
                request.effectiveFrom() != null ? request.effectiveFrom() : Instant.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(FinancialAttributesDto.fromDomain(created));
    }
}
