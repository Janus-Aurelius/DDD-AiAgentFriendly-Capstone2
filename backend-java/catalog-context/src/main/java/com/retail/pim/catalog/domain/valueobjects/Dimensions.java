package com.retail.pim.catalog.domain.valueobjects;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable Value Object representing physical logistics dimensions (L x W x H) and weight.
 * Pure Java - no ORM annotations.
 */
public final class Dimensions implements Serializable {

    private final BigDecimal length;
    private final BigDecimal width;
    private final BigDecimal height;
    private final BigDecimal weight;
    private final String unit;

    public Dimensions(BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal weight, String unit) {
        if (length == null || length.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Length must be non-null and greater than zero");
        }
        if (width == null || width.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Width must be non-null and greater than zero");
        }
        if (height == null || height.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Height must be non-null and greater than zero");
        }
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be non-null and greater than zero");
        }
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("Dimension unit cannot be blank");
        }

        this.length = length;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.unit = unit.trim();
    }

    public BigDecimal getLength() {
        return length;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public String getUnit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dimensions that = (Dimensions) o;
        return Objects.equals(length, that.length) &&
               Objects.equals(width, that.width) &&
               Objects.equals(height, that.height) &&
               Objects.equals(weight, that.weight) &&
               Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(length, width, height, weight, unit);
    }

    @Override
    public String toString() {
        return "Dimensions{" +
                "length=" + length +
                ", width=" + width +
                ", height=" + height +
                ", weight=" + weight +
                ", unit='" + unit + '\'' +
                '}';
    }
}
