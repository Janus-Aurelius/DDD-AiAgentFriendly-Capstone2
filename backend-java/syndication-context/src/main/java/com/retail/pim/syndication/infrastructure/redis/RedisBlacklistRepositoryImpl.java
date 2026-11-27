package com.retail.pim.syndication.infrastructure.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of RedisBlacklistRepository backed by Redis Set operations,
 * with concurrent in-memory fallback for local/standalone runtimes when Redis is unavailable.
 */
@Repository
public class RedisBlacklistRepositoryImpl implements RedisBlacklistRepository {

    public static final String BLACKLIST_KEY = "blacklisted_component_skus";

    private final RedisOperations<String, String> redisTemplate;
    private final Set<String> localFallbackBlacklist = ConcurrentHashMap.newKeySet();

    public RedisBlacklistRepositoryImpl(@Autowired(required = false) RedisOperations<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistComponent(String sku) {
        if (sku != null && !sku.isBlank()) {
            String trimmed = sku.trim();
            if (redisTemplate != null) {
                redisTemplate.opsForSet().add(BLACKLIST_KEY, trimmed);
            } else {
                localFallbackBlacklist.add(trimmed);
            }
        }
    }

    @Override
    public void unblacklistComponent(String sku) {
        if (sku != null && !sku.isBlank()) {
            String trimmed = sku.trim();
            if (redisTemplate != null) {
                redisTemplate.opsForSet().remove(BLACKLIST_KEY, trimmed);
            } else {
                localFallbackBlacklist.remove(trimmed);
            }
        }
    }

    @Override
    public boolean isComponentBlacklisted(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        String trimmed = sku.trim();
        if (redisTemplate != null) {
            Boolean member = redisTemplate.opsForSet().isMember(BLACKLIST_KEY, trimmed);
            return Boolean.TRUE.equals(member);
        }
        return localFallbackBlacklist.contains(trimmed);
    }

    @Override
    public List<Boolean> areComponentsBlacklisted(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyList();
        }
        if (redisTemplate != null) {
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
        List<Boolean> results = new ArrayList<>(skus.size());
        for (String sku : skus) {
            results.add(sku != null && localFallbackBlacklist.contains(sku.trim()));
        }
        return results;
    }
}
