package com.retail.pim.localization.application.commands;

import java.util.UUID;

public record ApplyRegionalOverrideCommand(
        UUID globalProductId,
        String regionCode,
        String attributeKey,
        String globalDefaultValue,
        String regionalValue,
        String reason
) {
    public ApplyRegionalOverrideCommand {
        if (globalProductId == null) throw new IllegalArgumentException("Global product ID required");
        if (regionCode == null || regionCode.isBlank()) throw new IllegalArgumentException("Region code required");
        if (attributeKey == null || attributeKey.isBlank()) throw new IllegalArgumentException("Attribute key required");
        if (regionalValue == null || regionalValue.isBlank()) throw new IllegalArgumentException("Regional value required");
    }
}
