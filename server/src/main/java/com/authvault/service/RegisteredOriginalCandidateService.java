package com.authvault.service;

import com.authvault.dto.asset.PerceptualMatchBand;

import java.util.List;

public interface RegisteredOriginalCandidateService {

    List<Candidate> findPlausibleCandidates(String submittedPerceptualHash);

    record Candidate(
            String assetId,
            String sha256,
            String storageKey,
            int pHashDistance,
            PerceptualMatchBand matchBand) {
    }
}
