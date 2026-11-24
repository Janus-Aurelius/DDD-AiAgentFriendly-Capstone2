package com.retail.pim.syndication.application.facades;

import com.retail.pim.syndication.infrastructure.redis.RedisBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SyndicationModuleFacade Application Facade Tests")
class SyndicationModuleFacadeTest {

    private RedisBlacklistRepository blacklistRepository;
    private SyndicationModuleFacade facade;

    @BeforeEach
    void setUp() {
        blacklistRepository = mock(RedisBlacklistRepository.class);
        facade = new SyndicationModuleFacadeImpl(blacklistRepository);
    }

    @Test
    @DisplayName("Should delegate blacklistComponent to RedisBlacklistRepository")
    void shouldBlacklistComponent() {
        facade.blacklistComponent("SKU-CBL");
        verify(blacklistRepository).blacklistComponent("SKU-CBL");
    }

    @Test
    @DisplayName("Should evaluate bundle availability: false if any component is blacklisted")
    void shouldEvaluateBundleAvailability() {
        List<String> components = List.of("SKU-CONSOLE", "SKU-CBL", "SKU-PAD");
        when(blacklistRepository.areComponentsBlacklisted(components))
                .thenReturn(List.of(false, true, false));

        boolean available = facade.isBundleAvailable(components);
        assertFalse(available, "Bundle must be unavailable when any component is blacklisted");

        List<String> blacklisted = facade.getBlacklistedComponents(components);
        assertEquals(List.of("SKU-CBL"), blacklisted);
    }

    @Test
    @DisplayName("Should evaluate bundle availability: true when all components are clear")
    void shouldReturnTrueWhenAllClear() {
        List<String> components = List.of("SKU-CONSOLE", "SKU-PAD");
        when(blacklistRepository.areComponentsBlacklisted(components))
                .thenReturn(List.of(false, false));

        boolean available = facade.isBundleAvailable(components);
        assertTrue(available);
        assertTrue(facade.getBlacklistedComponents(components).isEmpty());
    }
}
