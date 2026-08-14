package com.authvault.service;

import com.authvault.dto.asset.AssetAiAnalysisResponse;

public interface AiAssetAnalysisService {

    AssetAiAnalysisResponse analyzeOwnedImage(String assetId);
}
