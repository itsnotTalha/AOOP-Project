package com.authvault.service;

import com.authvault.dto.asset.AssetDetailResponse;
import com.authvault.dto.asset.AssetResponse;

import java.io.InputStream;
import java.util.List;

public interface AssetQueryService {

    List<AssetResponse> listOwnedAssets();

    AssetDetailResponse getOwnedAsset(String assetId);

    AssetDownload openOwnedAssetDownload(String assetId);

    record AssetDownload(InputStream inputStream, String filename, String mimeType) {
    }
}
