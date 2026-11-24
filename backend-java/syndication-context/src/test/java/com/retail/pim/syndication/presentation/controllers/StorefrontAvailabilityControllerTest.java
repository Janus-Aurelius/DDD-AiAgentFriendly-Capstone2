package com.retail.pim.syndication.presentation.controllers;

import com.retail.pim.syndication.application.facades.SyndicationModuleFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("StorefrontAvailabilityController REST API Tests")
class StorefrontAvailabilityControllerTest {

    private MockMvc mockMvc;
    private SyndicationModuleFacade syndicationFacade;

    @BeforeEach
    void setUp() {
        syndicationFacade = mock(SyndicationModuleFacade.class);
        StorefrontAvailabilityController controller = new StorefrontAvailabilityController(syndicationFacade);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/syndication/availability/bundles/{sku} should return available=true when no components are blacklisted")
    void shouldReturnAvailableWhenNoBlacklist() throws Exception {
        when(syndicationFacade.isBundleAvailable(List.of("SKU-CONSOLE", "SKU-PAD"))).thenReturn(true);

        mockMvc.perform(get("/api/v1/syndication/availability/bundles/BUNDLE-PS5")
                        .param("components", "SKU-CONSOLE", "SKU-PAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleSku").value("BUNDLE-PS5"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.blacklistedComponents").isEmpty())
                .andExpect(jsonPath("$.queryLatencyMs").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/syndication/availability/bundles/{sku} should return available=false when component is blacklisted")
    void shouldReturnUnavailableWhenComponentBlacklisted() throws Exception {
        List<String> components = List.of("SKU-CONSOLE", "SKU-CBL");
        when(syndicationFacade.isBundleAvailable(components)).thenReturn(false);
        when(syndicationFacade.getBlacklistedComponents(components)).thenReturn(List.of("SKU-CBL"));

        mockMvc.perform(get("/api/v1/syndication/availability/bundles/BUNDLE-PS5")
                        .param("components", "SKU-CONSOLE", "SKU-CBL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleSku").value("BUNDLE-PS5"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.blacklistedComponents[0]").value("SKU-CBL"))
                .andExpect(jsonPath("$.queryLatencyMs").isNumber());
    }
}
