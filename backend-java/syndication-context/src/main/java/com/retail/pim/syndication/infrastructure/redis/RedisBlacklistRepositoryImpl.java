package com.retail.pim.syndication.infrastructure.redis;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of RedisBlacklistRepository backed by Redis Set operations.
 */
@Repository
public class RedisBlacklistRepositoryImpl implements RedisBlacklistRepository {

    public static final String BLACKLIST_KEY = "blacklisted_component_skus";

    private final RedisOperations<String, String> redisTemplate;

    public RedisBlacklistRepositoryImpl(RedisOperations<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistComponent(String sku) {
        if (sku != null && !sku.isBlank()) {
            redisTemplate.opsForSet().add(BLACKLIST_KEY, sku.trim());
        }
    }

    @Override
    public void unblacklistComponent(String sku) {
        if (sku != null && !sku.isBlank()) {
            redisTemplate.opsForSet().remove(BLACKLIST_KEY, sku.trim());
        }
    }

    @Override
    public boolean isComponentBlacklisted(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        Boolean member = redisTemplate.opsForSet().isMember(BLACKLIST_KEY, sku.trim());
        return Boolean.TRUE.equals(member);
    }

    @Override
    public List<Boolean> areComponentsBlacklisted(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyList();
        }
        Object[] values = skus.stream().map(String::trim).toArray(Object[]::new);
        Map<Object, Boolean> resultMap = redisTemplate.opsForSet().isMember(BLACKLIST_KEY, values);
        List<Boolean> results = new ArrayList<>(skus.size());
        for (String sku : skus) {
            String trimmed = sku != null ? sku.trim() : "";
            Boolean val = resultMap != null ? resultMap.get(trimmed) : null;
            results.add(Boolean.TRUE.equals(val));
        }
        return results;
    }
}
