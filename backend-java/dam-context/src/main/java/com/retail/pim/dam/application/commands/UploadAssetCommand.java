package com.retail.pim.dam.application.commands;

import com.retail.pim.dam.domain.valueobjects.DRMLicense;

public record UploadAssetCommand(
        String fileName,
        String title,
        byte[] rawBytes,
        String mimeType,
        DRMLicense license
) {
    public UploadAssetCommand {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("File name required");
        if (rawBytes == null || rawBytes.length == 0) throw new IllegalArgumentException("File content required");
    }
}
