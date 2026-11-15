package com.retail.pim.catalog.application.commands;

import com.retail.pim.catalog.domain.valueobjects.AttributeValue;

import java.util.Map;
import java.util.UUID;

/**
 * Command to instantiate a new ProductFamily aggregate in DRAFT state.
 * Emitted when translating external vendor events or receiving direct catalog draft submissions.
 */
public record CreateDraftProductCommand(
        String code,
        String name,
        String description,
        UUID categoryId,
        Map<String, AttributeValue> attributes,
        String vendorItemId
) {
    public CreateDraftProductCommand {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Product code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
    }
}
