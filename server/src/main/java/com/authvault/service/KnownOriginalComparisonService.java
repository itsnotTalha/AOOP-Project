package com.authvault.service;

import com.authvault.dto.asset.KnownOriginalComparisonResponse;

public interface KnownOriginalComparisonService {

    KnownOriginalComparisonResponse compareWithKnownOriginal(String assetId);
}
