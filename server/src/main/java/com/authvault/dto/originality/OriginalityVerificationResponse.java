package com.authvault.dto.originality;

import com.authvault.dto.asset.PerceptualMatchBand;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record OriginalityVerificationResponse(
        OriginalityOutcome outcome,
        Submitted submitted,
        BlockchainLookup blockchainLookup,
        RegisteredOriginal registeredOriginal,
        Similarity similarity,
        Comparison comparison,
        AiEvidence aiEvidence,
        List<OriginalityReasonCode> reasonCodes,
        LocalDateTime analyzedAt) {

    public record Submitted(String sha256, String pHash) {
    }

    public record BlockchainLookup(String status, boolean exactMatch) {
    }

    public record RegisteredOriginal(
            String assetId,
            String creatorIdHash,
            String sha256,
            Instant registeredAt,
            String assetType,
            String verificationStatus,
            String evidenceHash) {
    }

    public record Similarity(int pHashDistance, PerceptualMatchBand band) {
    }

    public record Comparison(
            String status,
            Alignment alignment,
            Boolean differencePerformed,
            Double changedAreaRatio,
            Double meanAbsoluteDifference,
            Double structuralSimilarity,
            String changeMaskPngBase64) {
    }

    public record Alignment(
            String status,
            String method,
            int keypointsReference,
            int keypointsTarget,
            int goodMatches,
            int inliers,
            Double inlierRatio) {
    }

    public record AiEvidence(String status) {
    }
}
