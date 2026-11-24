package com.retail.pim.syndication.infrastructure.redis;

import java.util.List;

/**
 * Guardrail 5 / Goal 5: Redis Set Repository for sub-2ms Storefront Availability bundle masking.
 */
public interface RedisBlacklistRepository {

    void blacklistComponent(String sku);

    void unblacklistComponent(String sku);

    boolean isComponentBlacklisted(String sku);

    List<Boolean> areComponentsBlacklisted(List<String> skus);
}
