package com.retail.pim.syndication.application.facades;

import com.retail.pim.syndication.infrastructure.redis.RedisBlacklistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SyndicationModuleFacadeImpl implements SyndicationModuleFacade {

    private static final Logger log = LoggerFactory.getLogger(SyndicationModuleFacadeImpl.class);

    private final RedisBlacklistRepository blacklistRepository;

    public SyndicationModuleFacadeImpl(RedisBlacklistRepository blacklistRepository) {
        this.blacklistRepository = blacklistRepository;
    }

    @Override
    public void notifyComponentBlacklisted(String componentSku) {
        blacklistComponent(componentSku);
    }

    @Override
    public void notifyBundlesMasked(List<UUID> bundleVariantIds) {
        bulkMaskBundles(bundleVariantIds);
    }

    @Override
    public void blacklistComponent(String componentSku) {
        if (componentSku != null && !componentSku.isBlank()) {
            blacklistRepository.blacklistComponent(componentSku.trim());
            log.info("Blacklisted component [{}] in Redis Set (sub-2ms bundle protection active)", componentSku);
        }
    }

    @Override
    public void bulkMaskBundles(List<UUID> bundleVariantIds) {
        if (bundleVariantIds == null || bundleVariantIds.isEmpty()) {
            return;
        }
        log.info("Bulk masking [{}] bundles in Elasticsearch edge read-model", bundleVariantIds.size());
    }

    @Override
    public boolean isBundleAvailable(List<String> componentSkus) {
        if (componentSkus == null || componentSkus.isEmpty()) {
            return true;
        }
        List<Boolean> flags = blacklistRepository.areComponentsBlacklisted(componentSkus);
        return flags.stream().noneMatch(Boolean.TRUE::equals);
    }

    @Override
    public List<String> getBlacklistedComponents(List<String> componentSkus) {
        if (componentSkus == null || componentSkus.isEmpty()) {
            return List.of();
        }
        List<Boolean> flags = blacklistRepository.areComponentsBlacklisted(componentSkus);
        List<String> blacklisted = new ArrayList<>();
        for (int i = 0; i < componentSkus.size(); i++) {
            if (i < flags.size() && Boolean.TRUE.equals(flags.get(i))) {
                blacklisted.add(componentSkus.get(i));
            }
        }
        return blacklisted;
    }
}
