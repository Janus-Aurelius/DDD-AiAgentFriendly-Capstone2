package com.retail.pim.catalog.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.services.FamilyHoldUseCase;
import com.retail.pim.catalog.domain.enums.FamilyLifecycleState;
import com.retail.pim.catalog.domain.enums.ProductLifecycleState;
import com.retail.pim.catalog.domain.model.ProductFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Slice 6: AdministrativeHoldController REST API Tests (CUJ 6)")
class AdministrativeHoldControllerTest {

    private MockMvc mockMvc;
    private FamilyHoldUseCase holdUseCase;
    private ObjectMapper objectMapper;

    private final UUID familyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        holdUseCase = mock(FamilyHoldUseCase.class);
        AdministrativeHoldController controller = new AdministrativeHoldController(holdUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/catalog/families/{id}/holds applies hold and returns 200 OK")
    void applyHoldReturns200Ok() throws Exception {
        AdministrativeHoldController.HoldRequestDto request =
                new AdministrativeHoldController.HoldRequestDto("TRADEMARK_DISPUTE");

        ProductFamily family = new ProductFamily(
                familyId, "FAM-POLO", "Polo Shirt", "Desc",
                Map.of(), UUID.randomUUID(), ProductLifecycleState.PUBLISHED,
                Map.of(), null, 1, 2L, Set.of("TRADEMARK_DISPUTE"), FamilyLifecycleState.FAMILY_SUSPENDED
        );

        when(holdUseCase.applyHold(eq(familyId), eq("TRADEMARK_DISPUTE"), eq(1L))).thenReturn(family);

        mockMvc.perform(post("/api/v1/catalog/families/{id}/holds", familyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"1\"")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(familyId.toString()))
                .andExpect(jsonPath("$.code").value("FAM-POLO"))
                .andExpect(jsonPath("$.lifecycleState").value("FAMILY_SUSPENDED"))
                .andExpect(jsonPath("$.activeSuspensionReasons[0]").value("TRADEMARK_DISPUTE"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @DisplayName("DELETE /api/v1/catalog/families/{id}/holds/{reason} lifts hold and returns 200 OK")
    void liftHoldReturns200Ok() throws Exception {
        ProductFamily family = new ProductFamily(
                familyId, "FAM-POLO", "Polo Shirt", "Desc",
                Map.of(), UUID.randomUUID(), ProductLifecycleState.PUBLISHED,
                Map.of(), null, 1, 3L, Set.of(), FamilyLifecycleState.FAMILY_PUBLISHED
        );

        when(holdUseCase.liftHold(eq(familyId), eq("TRADEMARK_DISPUTE"), eq(2L))).thenReturn(family);

        mockMvc.perform(delete("/api/v1/catalog/families/{id}/holds/{reason}", familyId, "TRADEMARK_DISPUTE")
                        .header("If-Match", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(familyId.toString()))
                .andExpect(jsonPath("$.lifecycleState").value("FAMILY_PUBLISHED"))
                .andExpect(jsonPath("$.activeSuspensionReasons").isEmpty())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/families/{id}/holds returns active holds list")
    void getActiveHoldsReturns200Ok() throws Exception {
        when(holdUseCase.getActiveHolds(familyId)).thenReturn(Set.of("TRADEMARK_DISPUTE", "NO_ACTIVE_VARIANTS"));

        mockMvc.perform(get("/api/v1/catalog/families/{id}/holds", familyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
