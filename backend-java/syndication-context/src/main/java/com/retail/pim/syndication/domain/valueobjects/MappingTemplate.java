package com.retail.pim.syndication.domain.valueobjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable Value Object defining destination-specific transformation rules.
 * (e.g. "Amazon Title = Brand + Model + Color", max title 200 chars, strip HTML).
 */
public final class MappingTemplate implements Serializable {

    private final String channelName;
    private final int maxTitleLength;
    private final boolean stripHtml;
    private final boolean requirePrimaryImage;

    public MappingTemplate(String channelName, int maxTitleLength, boolean stripHtml, boolean requirePrimaryImage) {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Channel name cannot be blank");
        }
        this.channelName = channelName.trim();
        this.maxTitleLength = maxTitleLength > 0 ? maxTitleLength : 200;
        this.stripHtml = stripHtml;
        this.requirePrimaryImage = requirePrimaryImage;
    }

    public static MappingTemplate forAmazon() {
        return new MappingTemplate("Amazon_Marketplace_US", 200, true, true);
    }

    public static MappingTemplate forMobileApp() {
        return new MappingTemplate("Mobile_App_Storefront", 120, false, true);
    }

    public String transformTitle(String rawTitle, String brand, String model, String color) {
        String base = (rawTitle != null && !rawTitle.isBlank()) ? rawTitle.trim() : (brand + " " + model + " " + color);
        if (base.length() > maxTitleLength) {
            return base.substring(0, maxTitleLength - 3) + "...";
        }
        return base;
    }

    public String transformDescription(String rawHtmlDescription) {
        if (rawHtmlDescription == null) return "";
        if (stripHtml) {
            // Strip HTML tags and normalize whitespace
            return rawHtmlDescription.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        }
        return rawHtmlDescription.trim();
    }

    public String getChannelName() {
        return channelName;
    }

    public int getMaxTitleLength() {
        return maxTitleLength;
    }

    public boolean isStripHtml() {
        return stripHtml;
    }

    public boolean isRequirePrimaryImage() {
        return requirePrimaryImage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MappingTemplate that = (MappingTemplate) o;
        return maxTitleLength == that.maxTitleLength &&
               stripHtml == that.stripHtml &&
               requirePrimaryImage == that.requirePrimaryImage &&
               Objects.equals(channelName, that.channelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelName, maxTitleLength, stripHtml, requirePrimaryImage);
    }
}
