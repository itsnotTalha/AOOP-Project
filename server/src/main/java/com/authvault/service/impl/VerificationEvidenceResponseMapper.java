package com.authvault.service.impl;

import com.authvault.dto.verification.VerificationEvidenceResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.VerificationEvidence;
import org.springframework.stereotype.Component;

@Component
public class VerificationEvidenceResponseMapper {

    public VerificationEvidenceResponse toResponse(VerificationEvidence evidence) {
        DigitalAsset asset = evidence.getAsset();
        return new VerificationEvidenceResponse(
                evidence.getUuid(),
                asset.getUuid(),
                asset.getTitle(),
                asset.getAssetType().name(),
                asset.getVerificationStatus().name(),
                evidence.getSha256(),
                evidence.isExactDuplicateDetected(),
                evidence.getPerceptualHash(),
                evidence.getPerceptualHashStatus(),
                evidence.getSimilarCandidateCount(),
                evidence.getBestCandidateAssetUuid(),
                evidence.getBestPhashDistance(),
                evidence.isComparisonPerformed(),
                evidence.getComparisonStatus(),
                evidence.getComparisonReason(),
                evidence.getChangedAreaRatio(),
                evidence.getMeanAbsoluteDifference(),
                evidence.getStructuralSimilarity(),
                evidence.isFabricLookupPerformed(),
                evidence.getFabricStatus(),
                evidence.getFabricRegisteredOriginalFound(),
                evidence.getFabricReferenceAssetUuid(),
                evidence.getGeneratedAt(),
                evidence.getEvidenceVersion(),
                evidence.getEvidenceHash());
    }
}
