package com.retail.pim.syndication.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisBlacklistRepositoryTest {

    private RedisOperations<String, String> redisTemplate;
    private SetOperations<String, String> setOps;
    private RedisBlacklistRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        RedisOperations<String, String> mockRedis = mock(RedisOperations.class);
        redisTemplate = mockRedis;
        @SuppressWarnings("unchecked")
        SetOperations<String, String> mockSetOps = mock(SetOperations.class);
        setOps = mockSetOps;
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        repository = new RedisBlacklistRepositoryImpl(redisTemplate);
    }

    @Test
    @DisplayName("blacklistComponent should add SKU to Redis Set")
    void shouldBlacklistComponent() {
        repository.blacklistComponent("SKU-CBL");
        verify(setOps).add(RedisBlacklistRepositoryImpl.BLACKLIST_KEY, "SKU-CBL");
    }

    @Test
    @DisplayName("unblacklistComponent should remove SKU from Redis Set")
    void shouldUnblacklistComponent() {
        repository.unblacklistComponent("SKU-CBL");
        verify(setOps).remove(RedisBlacklistRepositoryImpl.BLACKLIST_KEY, "SKU-CBL");
    }

    @Test
    @DisplayName("isComponentBlacklisted should check membership in Redis Set")
    void shouldCheckMembership() {
        when(setOps.isMember(RedisBlacklistRepositoryImpl.BLACKLIST_KEY, "SKU-CBL")).thenReturn(true);
        when(setOps.isMember(RedisBlacklistRepositoryImpl.BLACKLIST_KEY, "SKU-OK")).thenReturn(false);

        assertTrue(repository.isComponentBlacklisted("SKU-CBL"));
        assertFalse(repository.isComponentBlacklisted("SKU-OK"));
        assertFalse(repository.isComponentBlacklisted(""));
        assertFalse(repository.isComponentBlacklisted(null));
    }

    @Test
    @DisplayName("areComponentsBlacklisted should check multi-membership in Redis Set")
    void shouldCheckMultiMembership() {
        when(setOps.isMember(eq(RedisBlacklistRepositoryImpl.BLACKLIST_KEY), any(Object[].class)))
                .thenReturn(java.util.Map.of("SKU-CBL", true, "SKU-OK", false));

        List<Boolean> results = repository.areComponentsBlacklisted(List.of("SKU-CBL", "SKU-OK"));
        assertEquals(2, results.size());
        assertTrue(results.get(0));
        assertFalse(results.get(1));
    }
}
