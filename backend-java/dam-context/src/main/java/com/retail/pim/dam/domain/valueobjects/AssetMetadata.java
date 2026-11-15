package com.retail.pim.dam.domain.valueobjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable Value Object encapsulating EXIF and technical file attributes.
 */
public final class AssetMetadata implements Serializable {

    private final int width;
    private final int height;
    private final String colorProfile;
    private final String cameraModel;
    private final String mimeType;
    private final long fileSizeBytes;

    public AssetMetadata(int width, int height, String colorProfile, String cameraModel, String mimeType, long fileSizeBytes) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive integers");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("Mime type cannot be blank");
        }
        this.width = width;
        this.height = height;
        this.colorProfile = (colorProfile != null && !colorProfile.isBlank()) ? colorProfile : "sRGB";
        this.cameraModel = cameraModel;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getColorProfile() {
        return colorProfile;
    }

    public String getCameraModel() {
        return cameraModel;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getResolutionString() {
        return width + "x" + height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetMetadata that = (AssetMetadata) o;
        return width == that.width &&
               height == that.height &&
               fileSizeBytes == that.fileSizeBytes &&
               Objects.equals(colorProfile, that.colorProfile) &&
               Objects.equals(cameraModel, that.cameraModel) &&
               Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, colorProfile, cameraModel, mimeType, fileSizeBytes);
    }
}
