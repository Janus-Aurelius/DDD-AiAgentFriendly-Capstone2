package com.retail.pim.catalog.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.pim.catalog.domain.valueobjects.AttributeValue;
import com.retail.pim.catalog.domain.valueobjects.DigitalAsset;
import com.retail.pim.catalog.domain.valueobjects.Dimensions;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA AttributeConverters for PostgreSQL JSONB columns.
 */
public class JsonbConverters {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Converter(autoApply = false)
    public static class AttributeMapConverter implements AttributeConverter<Map<String, AttributeValue>, String> {

        public static class AttributeDto {
            public String type;
            public Object value;
            public String unit;

            public AttributeDto() {}
            public AttributeDto(String type, Object value, String unit) {
                this.type = type;
                this.value = value;
                this.unit = unit;
            }
        }

        @Override
        public String convertToDatabaseColumn(Map<String, AttributeValue> attributeMap) {
            if (attributeMap == null || attributeMap.isEmpty()) {
                return "{}";
            }
            try {
                Map<String, AttributeDto> serialized = new HashMap<>();
                for (Map.Entry<String, AttributeValue> entry : attributeMap.entrySet()) {
                    AttributeValue av = entry.getValue();
                    serialized.put(entry.getKey(), new AttributeDto(av.getType().name(), av.getValue(), av.getUnit()));
                }
                return objectMapper.writeValueAsString(serialized);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing AttributeValue map to JSONB", e);
            }
        }

        @Override
        public Map<String, AttributeValue> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank() || dbData.equals("{}")) {
                return new HashMap<>();
            }
            try {
                Map<String, AttributeDto> rawMap = objectMapper.readValue(dbData, new TypeReference<>() {});
                Map<String, AttributeValue> result = new HashMap<>();
                for (Map.Entry<String, AttributeDto> entry : rawMap.entrySet()) {
                    AttributeDto dto = entry.getValue();
                    AttributeValue.AttributeType type = AttributeValue.AttributeType.valueOf(dto.type);
                    result.put(entry.getKey(), new AttributeValue(type, dto.value, dto.unit));
                }
                return result;
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing JSONB to AttributeValue map", e);
            }
        }
    }

    @Converter(autoApply = false)
    public static class LocalizedDescriptionsConverter implements AttributeConverter<Map<String, String>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            if (attribute == null || attribute.isEmpty()) return "{}";
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing localized descriptions to JSONB", e);
            }
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank() || dbData.equals("{}")) return new HashMap<>();
            try {
                return objectMapper.readValue(dbData, new TypeReference<>() {});
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing localized descriptions from JSONB", e);
            }
        }
    }

    @Converter(autoApply = false)
    public static class DimensionsConverter implements AttributeConverter<Dimensions, String> {
        public static class DimensionsDto {
            public BigDecimal length;
            public BigDecimal width;
            public BigDecimal height;
            public BigDecimal weight;
            public String unit;

            public DimensionsDto() {}
            public DimensionsDto(Dimensions d) {
                this.length = d.getLength();
                this.width = d.getWidth();
                this.height = d.getHeight();
                this.weight = d.getWeight();
                this.unit = d.getUnit();
            }
        }

        @Override
        public String convertToDatabaseColumn(Dimensions attribute) {
            if (attribute == null) return null;
            try {
                return objectMapper.writeValueAsString(new DimensionsDto(attribute));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing Dimensions to JSONB", e);
            }
        }

        @Override
        public Dimensions convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return null;
            try {
                DimensionsDto dto = objectMapper.readValue(dbData, DimensionsDto.class);
                return new Dimensions(dto.length, dto.width, dto.height, dto.weight, dto.unit);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing Dimensions from JSONB", e);
            }
        }
    }

    @Converter(autoApply = false)
    public static class DigitalAssetsConverter implements AttributeConverter<List<DigitalAsset>, String> {
        public static class AssetDto {
            public String id;
            public String url;
            public String type;
            public boolean primaryImage;

            public AssetDto() {}
            public AssetDto(DigitalAsset a) {
                this.id = a.getId().toString();
                this.url = a.getUrl();
                this.type = a.getType().name();
                this.primaryImage = a.isPrimaryImage();
            }
        }

        @Override
        public String convertToDatabaseColumn(List<DigitalAsset> attribute) {
            if (attribute == null || attribute.isEmpty()) return "[]";
            try {
                List<AssetDto> dtos = attribute.stream().map(AssetDto::new).toList();
                return objectMapper.writeValueAsString(dtos);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing DigitalAssets to JSONB", e);
            }
        }

        @Override
        public List<DigitalAsset> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank() || dbData.equals("[]")) return new ArrayList<>();
            try {
                List<AssetDto> dtos = objectMapper.readValue(dbData, new TypeReference<>() {});
                return dtos.stream()
                        .map(d -> new DigitalAsset(
                                UUID.fromString(d.id),
                                d.url,
                                DigitalAsset.AssetType.valueOf(d.type),
                                d.primaryImage
                        ))
                        .toList();
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing DigitalAssets from JSONB", e);
            }
        }
    }
}
