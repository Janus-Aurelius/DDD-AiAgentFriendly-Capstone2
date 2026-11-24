package com.retail.pim.catalog.application.compliance;

import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import com.retail.pim.catalog.domain.model.Variant;
import com.retail.pim.catalog.domain.repository.FinancialTemporalAuditRepository;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import com.retail.pim.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Slice 7: UOM Logistics Prerequisite Validator Tests (CUJ 7)")
class UomPrerequisiteValidatorTest {

    private InMemoryFinancialTemporalAuditRepository auditRepo;
    private UomPrerequisiteValidator validator;

    private final String sku = "SKU-BEVERAGE-01";

    @BeforeEach
    void setUp() {
        auditRepo = new InMemoryFinancialTemporalAuditRepository();
        validator = new UomPrerequisiteValidator(auditRepo);
    }

    @Test
    @DisplayName("Fails validation when Base UOM ('EACH') is missing in temporal financial registry")
    void failsWhenBaseUomIsMissing() {
        Variant variant = new Variant(
                UUID.randomUUID(), sku, "Orange Juice 1L", new BigDecimal("3.99"), "USD",
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"), new BigDecimal("1.05"), "CM_KG"),
                Map.of(), List.of(), false
        );
        variant.setGtin("012345678901"); // Valid GTIN-12

        assertThatThrownBy(() -> validator.validatePrerequisites(variant))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("lacks mandatory Base UOM tier [EACH]");
    }

    @Test
    @DisplayName("Fails validation when GTIN barcode is missing or invalid format")
    void failsWhenGtinIsMissingOrInvalid() {
        // Setup Base UOM in repo
        auditRepo.save(new FinancialAttributeHistory(
                UUID.randomUUID(), sku, "EACH", new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"),
                new BigDecimal("1.05"), "ESSENTIAL_GROCERIES", "HSN-2009", Instant.now()
        ));

        // Case 1: GTIN missing
        Variant variantNoGtin = new Variant(
                UUID.randomUUID(), sku, "Orange Juice 1L", new BigDecimal("3.99"), "USD",
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"), new BigDecimal("1.05"), "CM_KG"),
                Map.of(), List.of(), false
        );
        assertThatThrownBy(() -> validator.validatePrerequisites(variantNoGtin))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("missing mandatory GTIN barcode");

        // Case 2: Invalid non-numeric GTIN
        Variant variantInvalidGtin = new Variant(
                UUID.randomUUID(), sku, "Orange Juice 1L", new BigDecimal("3.99"), "USD",
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"), new BigDecimal("1.05"), "CM_KG"),
                Map.of(), List.of(), false
        );
        variantInvalidGtin.setGtin("INVALID_GTIN");
        assertThatThrownBy(() -> validator.validatePrerequisites(variantInvalidGtin))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Must be 12 to 14 numeric digits");
    }

    @Test
    @DisplayName("Fails validation when physical dimensions are missing")
    void failsWhenDimensionsAreInvalid() {
        auditRepo.save(new FinancialAttributeHistory(
                UUID.randomUUID(), sku, "EACH", new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"),
                new BigDecimal("1.05"), "ESSENTIAL_GROCERIES", "HSN-2009", Instant.now()
        ));

        // Missing dimensions
        Variant variantNoDims = new Variant(
                UUID.randomUUID(), sku, "Orange Juice 1L", new BigDecimal("3.99"), "USD",
                null,
                Map.of(), List.of(), false
        );
        variantNoDims.setGtin("012345678901");

        assertThatThrownBy(() -> validator.validatePrerequisites(variantNoDims))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("missing mandatory physical dimensions");
    }

    @Test
    @DisplayName("Succeeds when Base UOM, GTIN-12, and positive dimensions are all present")
    void succeedsWhenAllPrerequisitesAreSatisfied() {
        auditRepo.save(new FinancialAttributeHistory(
                UUID.randomUUID(), sku, "EACH", new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"),
                new BigDecimal("1.05"), "ESSENTIAL_GROCERIES", "HSN-2009", Instant.now()
        ));

        Variant compliantVariant = new Variant(
                UUID.randomUUID(), sku, "Orange Juice 1L", new BigDecimal("3.99"), "USD",
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("10.0"), new BigDecimal("25.0"), new BigDecimal("1.05"), "CM_KG"),
                Map.of(), List.of(), false
        );
        compliantVariant.setGtin("012345678905"); // GTIN-12

        assertThatCode(() -> validator.validatePrerequisites(compliantVariant))
                .doesNotThrowAnyException();
    }

    private static class InMemoryFinancialTemporalAuditRepository implements FinancialTemporalAuditRepository {
        private final List<FinancialAttributeHistory> storage = new ArrayList<>();

        @Override
        public FinancialAttributeHistory save(FinancialAttributeHistory history) {
            storage.add(history);
            return history;
        }

        @Override
        public Optional<FinancialAttributeHistory> findHistoricalSnapshot(String sku, String uomTier, Instant asOf) {
            return storage.stream()
                    .filter(h -> h.getSku().equalsIgnoreCase(sku) && h.getUomTier().equalsIgnoreCase(uomTier))
                    .findFirst();
        }

        @Override
        public Optional<FinancialAttributeHistory> findCurrentActive(String sku, String uomTier) {
            return storage.stream()
                    .filter(h -> h.getSku().equalsIgnoreCase(sku) && h.getUomTier().equalsIgnoreCase(uomTier))
                    .findFirst();
        }

        @Override
        public List<FinancialAttributeHistory> findHistoryBySku(String sku) {
            return storage;
        }
    }
}
