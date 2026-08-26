package com.authvault.dto.verification;

import java.time.LocalDateTime;

public record VerificationEvidenceResponse(
        String evidenceId,
        String assetId,
        String assetTitle,
        String assetType,
        String verificationStatus,
        String sha256,
        boolean exactDuplicateDetected,
        String perceptualHash,
        String perceptualHashStatus,
        Integer similarCandidateCount,
        String bestCandidateAssetId,
        Integer bestPhashDistance,
        boolean comparisonPerformed,
        String comparisonStatus,
        String comparisonReason,
        Double changedAreaRatio,
        Double meanAbsoluteDifference,
        Double structuralSimilarity,
        boolean fabricLookupPerformed,
        String fabricStatus,
        Boolean fabricRegisteredOriginalFound,
        String fabricReferenceAssetId,
        LocalDateTime generatedAt,
        String evidenceVersion,
        String evidenceHash) {
}
