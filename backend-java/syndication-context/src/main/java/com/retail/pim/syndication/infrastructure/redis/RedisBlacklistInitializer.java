package com.retail.pim.syndication.infrastructure.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cold-start synchronization: reads active suspended component SKUs from PostgreSQL
 * and executes pipeline SADD to `blacklisted_component_skus` in Redis.
 */
@Component
public class RedisBlacklistInitializer {

    private static final Logger log = LoggerFactory.getLogger(RedisBlacklistInitializer.class);
    public static final String BLACKLIST_KEY = RedisBlacklistRepositoryImpl.BLACKLIST_KEY;

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public RedisBlacklistInitializer(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeBlacklistOnStartup() {
        try {
            log.info("Starting RedisBlacklistInitializer cold-start synchronization...");
            String sql = """
                SELECT DISTINCT sku FROM catalog.product_variant 
                WHERE lifecycle_state = 'SKU_SUSPENDED' 
                   OR availability_status = 'RESTRICTED'
                """;
            List<String> suspendedSkus = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("sku"));
            if (suspendedSkus != null && !suspendedSkus.isEmpty()) {
                redisTemplate.opsForSet().add(BLACKLIST_KEY, suspendedSkus.toArray(new String[0]));
                log.info("Loaded {} suspended component SKUs into Redis Set [{}]", suspendedSkus.size(), BLACKLIST_KEY);
            } else {
                log.info("No suspended component SKUs found to initialize in Redis Set");
            }
        } catch (Exception e) {
            log.warn("RedisBlacklistInitializer cold-start skipped or failed (e.g. database/redis not reachable): {}", e.getMessage());
        }
    }
}
