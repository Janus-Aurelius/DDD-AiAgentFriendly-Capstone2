package com.retail.pim.catalog.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.retail.pim.catalog.application.services.FinancialMasterDataUseCase;
import com.retail.pim.catalog.domain.model.FinancialAttributeHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Slice 7: FinancialMasterDataController REST API Tests (CUJ 7)")
class FinancialMasterDataControllerTest {

    private MockMvc mockMvc;
    private FinancialMasterDataUseCase useCase;
    private ObjectMapper objectMapper;

    private final String sku = "SKU-JCE-ORANGE";

    @BeforeEach
    void setUp() {
        useCase = mock(FinancialMasterDataUseCase.class);
        FinancialMasterDataController controller = new FinancialMasterDataController(useCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/v1/catalog/skus/{sku}/financials with asOf returns 200 OK")
    void getHistoricalSnapshotReturns200() throws Exception {
        Instant asOf = Instant.parse("2026-10-25T12:00:00Z");
        FinancialAttributeHistory history = new FinancialAttributeHistory(
                UUID.randomUUID(), sku, "MASTER_CARTON",
                new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("25.00"),
                new BigDecimal("12.000"), "ESSENTIAL_GROCERIES", "HSN-2009",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), Instant.now()
        );

        when(useCase.getHistoricalSnapshot(eq(sku), eq("MASTER_CARTON"), eq(asOf)))
                .thenReturn(Optional.of(history));

        mockMvc.perform(get("/api/v1/catalog/skus/{sku}/financials", sku)
                        .param("uomTier", "MASTER_CARTON")
                        .param("asOf", asOf.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.uomTier").value("MASTER_CARTON"))
                .andExpect(jsonPath("$.grossWeightKg").value(12.0))
                .andExpect(jsonPath("$.taxBand").value("ESSENTIAL_GROCERIES"));
    }

    @Test
    @DisplayName("PUT /api/v1/catalog/skus/{sku}/financials updates attributes and returns 200 OK")
    void updateFinancialAttributesReturns200() throws Exception {
        FinancialMasterDataController.RecordFinancialAttributesRequest request =
                new FinancialMasterDataController.RecordFinancialAttributesRequest(
                        "MASTER_CARTON", new BigDecimal("40.00"), new BigDecimal("30.00"),
                        new BigDecimal("25.00"), new BigDecimal("14.000"),
                        "STANDARD_VAT", "HSN-2009", Instant.parse("2026-11-10T00:00:00Z")
                );

        FinancialAttributeHistory updated = new FinancialAttributeHistory(
                UUID.randomUUID(), sku, "MASTER_CARTON",
                new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("25.00"),
                new BigDecimal("14.000"), "STANDARD_VAT", "HSN-2009",
                Instant.parse("2026-11-10T00:00:00Z")
        );

        when(useCase.recordFinancialAttributes(
                eq(sku), eq("MASTER_CARTON"), eq(new BigDecimal("40.00")), eq(new BigDecimal("30.00")),
                eq(new BigDecimal("25.00")), eq(new BigDecimal("14.000")), eq("STANDARD_VAT"),
                eq("HSN-2009"), any(Instant.class)
        )).thenReturn(updated);

        mockMvc.perform(put("/api/v1/catalog/skus/{sku}/financials", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossWeightKg").value(14.0))
                .andExpect(jsonPath("$.taxBand").value("STANDARD_VAT"));
    }
}
