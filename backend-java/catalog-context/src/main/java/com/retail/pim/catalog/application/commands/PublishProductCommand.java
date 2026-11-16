package com.retail.pim.catalog.application.commands;

import java.util.UUID;

/**
 * Command to trigger the publish transition on an approved ProductFamily.
 * Carries targetLocale and targetChannel to enforce scope-specific compliance hard gates.
 */
public record PublishProductCommand(
        UUID productId,
        String targetLocale,
        String targetChannel,
        String requestedBy
) {
    public PublishProductCommand {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
        if (targetLocale == null || targetLocale.isBlank()) {
            targetLocale = "GLOBAL";
        }
        if (targetChannel == null || targetChannel.isBlank()) {
            targetChannel = "ALL";
        }
    }

    public PublishProductCommand(UUID productId, String requestedBy) {
        this(productId, "GLOBAL", "ALL", requestedBy);
    }
}
