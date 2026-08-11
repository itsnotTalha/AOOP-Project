package com.authvault.service;

import com.authvault.dto.asset.AssetVerificationResponse;

public interface AssetIntegrityService {

    AssetVerificationResponse verifyIntegrity(String assetId);
}
