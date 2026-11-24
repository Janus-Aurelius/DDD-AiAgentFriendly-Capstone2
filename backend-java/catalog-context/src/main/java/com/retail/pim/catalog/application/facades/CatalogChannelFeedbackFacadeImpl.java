package com.retail.pim.catalog.application.facades;

import com.retail.pim.catalog.application.services.ChannelFeedbackService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CatalogChannelFeedbackFacadeImpl implements CatalogChannelFeedbackFacade {

    private final ChannelFeedbackService feedbackService;

    public CatalogChannelFeedbackFacadeImpl(ChannelFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Override
    public ChannelFeedbackResult reportChannelRejection(String sku, String channel, long evaluatedVersion, String errorMessage) {
        return feedbackService.processRejection(sku, channel, evaluatedVersion, errorMessage);
    }

    @Override
    public ChannelFeedbackResult reportChannelSuccess(String sku, String channel, long evaluatedVersion) {
        return feedbackService.processSuccess(sku, channel, evaluatedVersion);
    }

    @Override
    public Map<String, String> getChannelStatuses(String sku) {
        return feedbackService.getChannelStatuses(sku);
    }
}
