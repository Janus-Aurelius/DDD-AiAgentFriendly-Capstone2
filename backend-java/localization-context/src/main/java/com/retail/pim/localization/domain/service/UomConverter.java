package com.retail.pim.localization.domain.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain Service: Deterministic Unit of Measure (UoM) Conversion.
 * Eliminates manual human conversion drift when fanning out global items
 * to metric regions (Canada CA, Mexico MX, Europe EU).
 */
@Service
public class UomConverter {

    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(lbs?|pounds?|kg|kilograms?)$");
    private static final Pattern LENGTH_PATTERN = Pattern.compile("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(in|inches|\"|cm|centimeters?)$");

    private static final BigDecimal LBS_TO_KG_RATIO = new BigDecimal("0.45359237");
    private static final BigDecimal IN_TO_CM_RATIO = new BigDecimal("2.54");

    /**
     * Converts a raw attribute value to the appropriate regional standard unit.
     * For CA and MX, imperial units (lbs, in) are automatically converted to metric (kg, cm).
     */
    public String convertForRegion(String attributeKey, String rawValue, String regionCode) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }

        String targetRegion = regionCode != null ? regionCode.trim().toUpperCase() : "US";

        // CA and MX require metric standardization
        if ("CA".equals(targetRegion) || "MX".equals(targetRegion)) {
            // Check weight conversion
            Matcher weightMatcher = WEIGHT_PATTERN.matcher(rawValue.trim());
            if (weightMatcher.matches()) {
                String unit = weightMatcher.group(2).toLowerCase();
                if (unit.startsWith("lb") || unit.startsWith("pound")) {
                    BigDecimal val = new BigDecimal(weightMatcher.group(1));
                    BigDecimal kg = val.multiply(LBS_TO_KG_RATIO).setScale(2, RoundingMode.HALF_UP);
                    return kg + " kg";
                }
            }

            // Check dimension/length conversion
            Matcher lengthMatcher = LENGTH_PATTERN.matcher(rawValue.trim());
            if (lengthMatcher.matches()) {
                String unit = lengthMatcher.group(2).toLowerCase();
                if (unit.startsWith("in") || "\"".equals(unit)) {
                    BigDecimal val = new BigDecimal(lengthMatcher.group(1));
                    BigDecimal cm = val.multiply(IN_TO_CM_RATIO).setScale(2, RoundingMode.HALF_UP);
                    return cm + " cm";
                }
            }
        }

        return rawValue;
    }
}
