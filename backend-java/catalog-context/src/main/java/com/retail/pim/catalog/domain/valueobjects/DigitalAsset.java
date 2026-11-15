package com.retail.pim.catalog.domain.valueobjects;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Value Object representing digital media attached to a Variant (SKU).
 */
public final class DigitalAsset implements Serializable {

    public enum AssetType {
        IMAGE,
        DOCUMENT,
        SPEC_SHEET,
        VIDEO
    }

    private final UUID id;
    private final String url;
    private final AssetType type;
    private final boolean primaryImage;

    public DigitalAsset(UUID id, String url, AssetType type, boolean primaryImage) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset URL cannot be empty");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.url = url.trim();
        this.type = type != null ? type : AssetType.IMAGE;
        this.primaryImage = primaryImage;
    }

    public static DigitalAsset primaryImage(String url) {
        return new DigitalAsset(UUID.randomUUID(), url, AssetType.IMAGE, true);
    }

    public static DigitalAsset galleryImage(String url) {
        return new DigitalAsset(UUID.randomUUID(), url, AssetType.IMAGE, false);
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public AssetType getType() {
        return type;
    }

    public boolean isPrimaryImage() {
        return primaryImage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DigitalAsset that = (DigitalAsset) o;
        return primaryImage == that.primaryImage &&
               Objects.equals(url, that.url) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, type, primaryImage);
    }

    @Override
    public String toString() {
        return "DigitalAsset{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", type=" + type +
                ", primaryImage=" + primaryImage +
                '}';
    }
}
