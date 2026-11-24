package com.retail.pim.catalog.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.application.services.VariantRevisionUseCase;
import com.retail.pim.catalog.domain.enums.RevisionState;
import com.retail.pim.catalog.domain.model.VariantRevision;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("VariantRevisionController REST API Tests")
class VariantRevisionControllerTest {

    private MockMvc mockMvc;
    private VariantRevisionUseCase revisionService;
    private ObjectMapper objectMapper;

    private final String sku = "SKU-TSHIRT-RED";
    private final UUID variantId = UUID.randomUUID();
    private final UUID revisionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        revisionService = mock(VariantRevisionUseCase.class);
        VariantRevisionController controller = new VariantRevisionController(revisionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/catalog/variants/{sku}/revisions should create working revision and return 201 Created")
    void shouldCreateWorkingRevision() throws Exception {
        VariantRevision revision = new VariantRevision(
                revisionId,
                variantId,
                1L,
                RevisionState.WORKING_DRAFT,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        when(revisionService.createWorkingRevision(sku)).thenReturn(revision);

        mockMvc.perform(post("/api/v1/catalog/variants/{sku}/revisions", sku))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(revisionId.toString()))
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.baseVersion").value(1))
                .andExpect(jsonPath("$.state").value("WORKING_DRAFT"));

        verify(revisionService).createWorkingRevision(sku);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/variants/{sku}/revisions/{id} should return 200 OK with revision details")
    void shouldGetRevision() throws Exception {
        VariantRevision revision = new VariantRevision(
                revisionId,
                variantId,
                1L,
                RevisionState.WORKING_DRAFT,
                Map.of("color", AttributeValue.ofString("Red")),
                Instant.now(),
                Instant.now()
        );
        when(revisionService.getRevision(revisionId)).thenReturn(revision);

        mockMvc.perform(get("/api/v1/catalog/variants/{sku}/revisions/{id}", sku, revisionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(revisionId.toString()))
                .andExpect(jsonPath("$.remediatedAttributes.color").value("Red"));
    }

    @Test
    @DisplayName("PUT /api/v1/catalog/variants/{sku}/revisions/{id} should update attributes and return 200 OK")
    void shouldUpdateWorkingRevision() throws Exception {
        VariantRevision.createWorkingCopy(variantId, 1L);
        VariantRevision updated = new VariantRevision(
                revisionId,
                variantId,
                1L,
                RevisionState.WORKING_DRAFT,
                Map.of("prop65_warning_label", AttributeValue.ofString("Warning text")),
                Instant.now(),
                Instant.now()
        );
        when(revisionService.updateWorkingRevision(eq(revisionId), any())).thenReturn(updated);

        VariantRevisionController.UpdateRevisionRequestDto request = new VariantRevisionController.UpdateRevisionRequestDto(
                Map.of("prop65_warning_label", "Warning text")
        );

        mockMvc.perform(put("/api/v1/catalog/variants/{sku}/revisions/{id}", sku, revisionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remediatedAttributes.prop65_warning_label").value("Warning text"));
    }

    @Test
    @DisplayName("POST /api/v1/catalog/variants/{sku}/revisions/{id}/submit should submit for review and return 200 OK")
    void shouldSubmitForReview() throws Exception {
        VariantRevision submitted = new VariantRevision(
                revisionId,
                variantId,
                1L,
                RevisionState.APPROVED,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        when(revisionService.submitForReview(revisionId)).thenReturn(submitted);

        mockMvc.perform(post("/api/v1/catalog/variants/{sku}/revisions/{id}/submit", sku, revisionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/v1/catalog/variants/{sku}/revisions/{id}/promote should promote revision and return 200 OK")
    void shouldPromoteRevision() throws Exception {
        VariantRevision promoted = new VariantRevision(
                revisionId,
                variantId,
                1L,
                RevisionState.PROMOTED,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        when(revisionService.promoteRevision(revisionId)).thenReturn(promoted);

        mockMvc.perform(post("/api/v1/catalog/variants/{sku}/revisions/{id}/promote", sku, revisionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROMOTED"));
    }
}
