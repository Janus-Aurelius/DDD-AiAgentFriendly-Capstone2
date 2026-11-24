package com.retail.pim.catalog.application.facades;

import java.util.Map;

/**
 * Public Application Facade in catalog-context for receiving omnichannel marketplace feed results.
 * Enforces version pinning to prevent stale asynchronous feed rejections from thrashing published state.
 */
public interface CatalogChannelFeedbackFacade {

    ChannelFeedbackResult reportChannelRejection(String sku, String channel, long evaluatedVersion, String errorMessage);

    ChannelFeedbackResult reportChannelSuccess(String sku, String channel, long evaluatedVersion);

    Map<String, String> getChannelStatuses(String sku);

    enum ChannelFeedbackResult {
        APPLIED,
        DISCARDED_STALE
    }
}
