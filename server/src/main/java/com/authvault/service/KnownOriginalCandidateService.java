package com.authvault.service;

import com.authvault.dto.asset.SimilarImagesResponse;

public interface KnownOriginalCandidateService {

    SimilarImagesResponse findSimilarImages(String assetId);
}
