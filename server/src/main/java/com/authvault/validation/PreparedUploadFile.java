package com.authvault.validation;

import com.authvault.entity.DigitalAsset;

public record PreparedUploadFile(
        String displayFilename,
        String extension,
        DigitalAsset.AssetType assetType,
        long maxFileSizeBytes) {
}
