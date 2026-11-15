package com.retail.pim.catalog.application.commands;

import java.util.UUID;

/**
 * Command to trigger the publish transition on an approved ProductFamily.
 */
public record PublishProductCommand(
        UUID productId,
        String requestedBy
) {
    public PublishProductCommand {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
    }
}
