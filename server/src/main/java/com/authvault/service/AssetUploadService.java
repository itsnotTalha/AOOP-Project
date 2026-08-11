package com.authvault.service;

import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.AssetUploadRequest;

public interface AssetUploadService {

    AssetResponse uploadImage(AssetUploadRequest request);

    AssetResponse uploadDocument(AssetUploadRequest request);
}
