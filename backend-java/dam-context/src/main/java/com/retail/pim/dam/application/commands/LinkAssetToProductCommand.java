package com.retail.pim.dam.application.commands;

import java.util.UUID;

public record LinkAssetToProductCommand(
        UUID assetId,
        UUID productId,
        String sku,
        boolean isPrimaryImage
) {
    public LinkAssetToProductCommand {
        if (assetId == null) throw new IllegalArgumentException("Asset ID required");
        if (productId == null) throw new IllegalArgumentException("Product ID required");
    }
}
