package com.authvault.service;

import com.authvault.dto.verification.VerificationEvidenceResponse;

import java.util.List;

public interface VerificationEvidenceService {

    VerificationEvidenceResponse generateEvidence(String assetId);

    VerificationEvidenceResponse getLatestEvidence(String assetId);

    List<VerificationEvidenceResponse> getEvidenceHistory(String assetId);
}
