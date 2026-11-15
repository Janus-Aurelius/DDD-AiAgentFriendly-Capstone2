package com.retail.pim.syndication.domain.model;

import com.retail.pim.shared.domain.AggregateRoot;
import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.syndication.domain.valueobjects.MappingTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ChannelCatalog: The Aggregate Root of the Channel Syndication Context.
 * Represents the complete syndicated catalog feed bound for a specific edge channel
 * (e.g. Amazon US, Mobile App Cache, Google Merchant).
 */
public class ChannelCatalog extends AggregateRoot<UUID> {

    private final String channelCode; // e.g. "Amazon_Marketplace_US"
    private final String destinationEndpoint;
    private MappingTemplate mappingTemplate;
    private final Map<String, ChannelItemProjection> itemsBySku = new HashMap<>();
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public ChannelCatalog(UUID id, String channelCode, String destinationEndpoint,
                          MappingTemplate mappingTemplate, boolean active) {
        super(id != null ? id : UUID.randomUUID());
        if (channelCode == null || channelCode.isBlank()) {
            throw new DomainException("Channel code is required");
        }
        this.channelCode = channelCode.trim();
        this.destinationEndpoint = destinationEndpoint;
        this.mappingTemplate = mappingTemplate != null ? mappingTemplate : MappingTemplate.forAmazon();
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void upsertProjection(ChannelItemProjection projection) {
        if (projection == null) {
            throw new DomainException("Projection cannot be null");
        }
        this.itemsBySku.put(projection.getSku(), projection);
        this.updatedAt = Instant.now();
    }

    /**
     * The Transformation Rule: Transforms flattened projection into destination-ready payload
     * according to the channel's MappingTemplate.
     */
    public Map<String, Object> transformForExport(ChannelItemProjection projection) {
        if (mappingTemplate.isRequirePrimaryImage() &&
            (projection.getPrimaryImageUrl() == null || projection.getPrimaryImageUrl().isBlank())) {
            throw new DomainException(String.format(
                    "Syndication to [%s] blocked for SKU [%s]: Primary image is strictly required by channel template",
                    this.channelCode, projection.getSku()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", projection.getSku());
        payload.put("title", mappingTemplate.transformTitle(projection.getTitle(), "", "", ""));
        payload.put("description", mappingTemplate.transformDescription(projection.getDescriptionHtml()));
        payload.put("price", projection.getPrice());
        payload.put("currency", projection.getCurrency());
        payload.put("primary_image", projection.getPrimaryImageUrl());
        payload.put("gallery_images", projection.getGalleryImageUrls());
        payload.put("attributes", projection.getFlattenedAttributes());
        payload.put("syndicated_channel", this.channelCode);
        payload.put("exported_at", Instant.now().toString());

        return payload;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getDestinationEndpoint() {
        return destinationEndpoint;
    }

    public MappingTemplate getMappingTemplate() {
        return mappingTemplate;
    }

    public Map<String, ChannelItemProjection> getItemsBySku() {
        return Collections.unmodifiableMap(itemsBySku);
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
