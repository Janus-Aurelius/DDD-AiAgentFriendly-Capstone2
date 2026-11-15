package com.retail.pim.catalog.domain.valueobjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable Value Object representing dynamic, schema-less attribute values stored in Postgres JSONB.
 * Pure Java - zero framework annotations.
 */
public final class AttributeValue implements Serializable {

    public enum AttributeType {
        STRING,
        NUMBER,
        BOOLEAN,
        JSON,
        LOCALIZED_STRING
    }

    private final AttributeType type;
    private final Object value;
    private final String unit;

    public AttributeValue(AttributeType type, Object value, String unit) {
        if (type == null) {
            throw new IllegalArgumentException("AttributeType cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Attribute value cannot be null");
        }
        this.type = type;
        this.value = value;
        this.unit = unit;
    }

    public AttributeValue(AttributeType type, Object value) {
        this(type, value, null);
    }

    public static AttributeValue ofString(String value) {
        return new AttributeValue(AttributeType.STRING, value);
    }

    public static AttributeValue ofNumber(Number value, String unit) {
        return new AttributeValue(AttributeType.NUMBER, value, unit);
    }

    public static AttributeValue ofBoolean(Boolean value) {
        return new AttributeValue(AttributeType.BOOLEAN, value);
    }

    public AttributeType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String asString() {
        return value.toString();
    }

    public Number asNumber() {
        if (value instanceof Number n) {
            return n;
        }
        return Double.parseDouble(value.toString());
    }

    public Boolean asBoolean() {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributeValue that = (AttributeValue) o;
        return type == that.type &&
               Objects.equals(value, that.value) &&
               Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, unit);
    }

    @Override
    public String toString() {
        return "AttributeValue{" +
                "type=" + type +
                ", value=" + value +
                ", unit='" + unit + '\'' +
                '}';
    }
}
