package com.authvault.service;

import com.authvault.dto.asset.AssetDetailResponse;
import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.VerificationHistoryResponse;

import java.io.InputStream;
import java.util.List;

public interface AssetQueryService {

    List<AssetResponse> listOwnedAssets(String type, String status, String search, String sort);

    default List<AssetResponse> listOwnedAssets() {
        return listOwnedAssets(null, null, null, null);
    }

    AssetDetailResponse getOwnedAsset(String assetId);

    List<VerificationHistoryResponse> getOwnedAssetVerificationHistory(String assetId);

    AssetDownload openOwnedAssetDownload(String assetId);

    record AssetDownload(InputStream inputStream, String filename, String mimeType) {
    }
}
