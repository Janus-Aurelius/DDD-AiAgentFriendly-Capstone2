package com.retail.pim.catalog.application.compliance;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.FinancialTemporalAuditRepository;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.exceptions.DomainException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates mandatory Unit-of-Measure (UOM) logistics and financial prerequisites
 * before a variant can transition to SKU_ACTIVE (CUJ 7).
 *
 * Enforces:
 * 1. Base UOM tier ("EACH") must be established in the bi-temporal master data repository.
 * 2. Valid GTIN-12 / GTIN-13 / GTIN-14 barcode must be present on the SKU variant.
 * 3. Physical dimensions (length, width, height) and gross weight must be strictly positive.
 */
@Component
public class UomPrerequisiteValidator {

    private static final Pattern GTIN_PATTERN = Pattern.compile("^[0-9]{12,14}$");
    public static final String BASE_UOM_TIER = "EACH";

    private final FinancialTemporalAuditRepository financialAuditRepository;

    public UomPrerequisiteValidator(FinancialTemporalAuditRepository financialAuditRepository) {
        this.financialAuditRepository = financialAuditRepository;
    }

    /**
     * Asserts that all mandatory logistics and UOM prerequisites are satisfied.
     * Throws DomainException with comprehensive diagnostics if any prerequisite is violated.
     */
    public void validatePrerequisites(Variant variant) {
        if (variant == null) {
            throw new DomainException("Variant cannot be null for UOM prerequisite validation");
        }

        List<String> errors = new ArrayList<>();

        // 1. GTIN Barcode Verification (GTIN-12, GTIN-13, GTIN-14)
        String gtin = variant.getGtin();
        if (gtin == null || gtin.isBlank()) {
            errors.add(String.format("SKU [%s] is missing mandatory GTIN barcode", variant.getSku()));
        } else if (!GTIN_PATTERN.matcher(gtin.trim()).matches()) {
            errors.add(String.format("SKU [%s] has invalid GTIN barcode [%s]: Must be 12 to 14 numeric digits",
                    variant.getSku(), gtin));
        }

        // 2. Physical Variant Dimensions Verification
        Dimensions dims = variant.getDimensions();
        if (dims == null) {
            errors.add(String.format("SKU [%s] is missing mandatory physical dimensions", variant.getSku()));
        } else {
            if (dims.getLength() == null || dims.getLength().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(String.format("SKU [%s] dimension length must be > 0", variant.getSku()));
            }
            if (dims.getWidth() == null || dims.getWidth().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(String.format("SKU [%s] dimension width must be > 0", variant.getSku()));
            }
            if (dims.getHeight() == null || dims.getHeight().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(String.format("SKU [%s] dimension height must be > 0", variant.getSku()));
            }
            if (dims.getWeight() == null || dims.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(String.format("SKU [%s] gross weight must be > 0", variant.getSku()));
            }
        }

        // 3. Base UOM Tier ("EACH") Existence Check
        Optional<FinancialAttributeHistory> baseUomRecord = financialAuditRepository.findCurrentActive(
                variant.getSku(), BASE_UOM_TIER
        );
        if (baseUomRecord.isEmpty()) {
            errors.add(String.format("SKU [%s] lacks mandatory Base UOM tier [%s] in temporal financial registry",
                    variant.getSku(), BASE_UOM_TIER));
        }

        if (!errors.isEmpty()) {
            throw new DomainException(String.format("UOM prerequisite validation failed for SKU [%s]: %s",
                    variant.getSku(), String.join("; ", errors)));
        }
    }
}
