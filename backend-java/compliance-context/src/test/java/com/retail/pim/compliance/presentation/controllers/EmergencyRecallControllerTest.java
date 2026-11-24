package com.retail.pim.compliance.presentation.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.compliance.application.services.EmergencyRecallUseCase;
import com.retail.pim.compliance.domain.events.ProductLotRecallInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("EmergencyRecallController REST API Tests")
class EmergencyRecallControllerTest {

    private MockMvc mockMvc;
    private EmergencyRecallUseCase recallService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        recallService = mock(EmergencyRecallUseCase.class);
        EmergencyRecallController controller = new EmergencyRecallController(recallService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/compliance/recalls should return 202 Accepted with recall confirmation")
    void shouldInitiateRecallAndReturn202Accepted() throws Exception {
        ProductLotRecallInitiatedEvent event = new ProductLotRecallInitiatedEvent(
                "SKU-INF",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination detected"
        );
        when(recallService.initiateRecall(any(), any(), any(), any())).thenReturn(event);

        EmergencyRecallController.RecallRequestDto request = new EmergencyRecallController.RecallRequestDto(
                "SKU-INF",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination detected"
        );

        mockMvc.perform(post("/api/v1/compliance/recalls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sku").value("SKU-INF"))
                .andExpect(jsonPath("$.lotNumber").value("LOT-9042"))
                .andExpect(jsonPath("$.severity").value("CLASS_1_CRITICAL_SAFETY"))
                .andExpect(jsonPath("$.status").value("RECALL_INITIATED_QUARANTINED"))
                .andExpect(jsonPath("$.eventId").value(event.eventId().toString()));

        verify(recallService).initiateRecall("SKU-INF", "LOT-9042", "CLASS_1_CRITICAL_SAFETY", "Bacterial contamination detected");
    }

    @Test
    @DisplayName("POST /api/v1/compliance/recalls should return 400 Bad Request when SKU is missing")
    void shouldReturn400WhenSkuMissing() throws Exception {
        EmergencyRecallController.RecallRequestDto request = new EmergencyRecallController.RecallRequestDto(
                "",
                "LOT-9042",
                "CLASS_1_CRITICAL_SAFETY",
                "Bacterial contamination"
        );

        mockMvc.perform(post("/api/v1/compliance/recalls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(recallService, never()).initiateRecall(any(), any(), any(), any());
    }
}
