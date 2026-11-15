package com.retail.pim.dam.domain.model;

import com.retail.pim.shared.domain.Entity;

import java.util.UUID;

/**
 * Rendition Entity: Represents a concrete physical variation of a digital asset
 * optimized for a specific downstream channel (e.g. mobile web, thumbnail, hi-res print).
 */
public class Rendition extends Entity<UUID> {

    public enum RenditionType {
        ORIGINAL_4K,
        MOBILE_WEB_OPTIMIZED,
        THUMBNAIL_100X100,
        PRINT_HIGH_RES
    }

    private final RenditionType type;
    private final String s3Url;
    private final String format; // webp, jpeg, tiff
    private final int width;
    private final int height;
    private final long fileSizeBytes;

    public Rendition(UUID id, RenditionType type, String s3Url, String format, int width, int height, long fileSizeBytes) {
        super(id != null ? id : UUID.randomUUID());
        if (type == null) throw new IllegalArgumentException("Rendition type cannot be null");
        if (s3Url == null || s3Url.isBlank()) throw new IllegalArgumentException("S3 URL cannot be blank");
        this.type = type;
        this.s3Url = s3Url.trim();
        this.format = format != null ? format.toLowerCase() : "webp";
        this.width = width;
        this.height = height;
        this.fileSizeBytes = fileSizeBytes;
    }

    public RenditionType getType() {
        return type;
    }

    public String getS3Url() {
        return s3Url;
    }

    public String getFormat() {
        return format;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }
}
