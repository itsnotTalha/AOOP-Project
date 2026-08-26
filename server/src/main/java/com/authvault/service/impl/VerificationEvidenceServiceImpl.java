package com.authvault.service.impl;

import com.authvault.dto.asset.KnownOriginalComparisonResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResult;
import com.authvault.dto.asset.SimilarImageMatchResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.verification.VerificationEvidenceResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationEvidence;
import com.authvault.exception.BlockchainException;
import com.authvault.exception.DuplicateResourceException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.exception.VerificationException;
import com.authvault.repository.AuthenticatorReviewRepository;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationEvidenceRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.BlockchainRegistryService;
import com.authvault.service.KnownOriginalCandidateService;
import com.authvault.service.KnownOriginalComparisonService;
import com.authvault.service.Sha256Service;
import com.authvault.service.VerificationEvidenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VerificationEvidenceServiceImpl implements VerificationEvidenceService {

    private static final String EVIDENCE_VERSION = "VAULTCHAIN_VERIFICATION_EVIDENCE_V1";

    private final DigitalAssetRepository digitalAssetRepository;
    private final VerificationEvidenceRepository evidenceRepository;
    private final AuthenticatorReviewRepository reviewRepository;
    private final KnownOriginalCandidateService candidateService;
    private final KnownOriginalComparisonService comparisonService;
    private final BlockchainRegistryService blockchainRegistryService;
    private final Sha256Service sha256Service;
    private final VerificationEvidenceResponseMapper responseMapper;

    public VerificationEvidenceServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            VerificationEvidenceRepository evidenceRepository,
            AuthenticatorReviewRepository reviewRepository,
            KnownOriginalCandidateService candidateService,
            KnownOriginalComparisonService comparisonService,
            BlockchainRegistryService blockchainRegistryService,
            Sha256Service sha256Service,
            VerificationEvidenceResponseMapper responseMapper) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.evidenceRepository = evidenceRepository;
        this.reviewRepository = reviewRepository;
        this.candidateService = candidateService;
        this.comparisonService = comparisonService;
        this.blockchainRegistryService = blockchainRegistryService;
        this.sha256Service = sha256Service;
        this.responseMapper = responseMapper;
    }

    @Override
    @Transactional
    public VerificationEvidenceResponse generateEvidence(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        if (reviewRepository.existsByEvidence_Asset(asset)) {
            throw new DuplicateResourceException("Verification has already been finalized");
        }
        if (evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset).isPresent()) {
            throw new DuplicateResourceException("Verification evidence is already pending review");
        }

        VerificationEvidence evidence = new VerificationEvidence();
        evidence.setUuid(UUID.randomUUID().toString());
        evidence.setAsset(asset);
        evidence.setSha256(asset.getSha256Hash());
        evidence.setExactDuplicateDetected(digitalAssetRepository
                .existsBySha256HashAndUuidNot(asset.getSha256Hash(), asset.getUuid()));
        evidence.setGeneratedAt(LocalDateTime.now());
        evidence.setEvidenceVersion(EVIDENCE_VERSION);

        captureImageEvidence(asset, evidence);
        captureFabricEvidence(asset, evidence);
        evidence.setEvidenceHash(hashEvidence(evidence));

        asset.setVerificationStatus(DigitalAsset.VerificationStatus.PENDING_REVIEW);
        digitalAssetRepository.save(asset);
        VerificationEvidence saved = evidenceRepository.save(evidence);
        return responseMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationEvidenceResponse getLatestEvidence(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        return responseMapper.toResponse(latestEvidence(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationEvidenceResponse> getEvidenceHistory(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        return evidenceRepository.findByAssetOrderByGeneratedAtDesc(asset).stream()
                .map(responseMapper::toResponse)
                .toList();
    }

    private void captureImageEvidence(
            DigitalAsset asset,
            VerificationEvidence evidence) {
        if (asset.getAssetType() != DigitalAsset.AssetType.IMAGE) {
            evidence.setPerceptualHashStatus("NOT_APPLICABLE_DOCUMENT");
            evidence.setComparisonPerformed(false);
            evidence.setComparisonReason("NOT_APPLICABLE_DOCUMENT");
            return;
        }

        evidence.setPerceptualHash(asset.getPerceptualHash());
        try {
            SimilarImagesResponse candidates = candidateService.findSimilarImages(asset.getUuid());
            List<SimilarImageMatchResponse> matches = candidates.getClosestMatches() == null
                    ? List.of()
                    : candidates.getClosestMatches();
            evidence.setPerceptualHash(candidates.getPerceptualHash());
            evidence.setPerceptualHashStatus("COMPLETED");
            evidence.setSimilarCandidateCount(matches.size());
            if (matches.isEmpty()) {
                evidence.setComparisonPerformed(false);
                evidence.setComparisonReason("NO_KNOWN_ORIGINAL");
                return;
            }

            SimilarImageMatchResponse best = matches.getFirst();
            evidence.setBestCandidateAssetUuid(best.getMatchedAssetId());
            evidence.setBestPhashDistance(best.getHammingDistance());
            captureComparisonEvidence(asset, evidence);
        } catch (RuntimeException exception) {
            evidence.setPerceptualHashStatus("UNAVAILABLE");
            evidence.setComparisonPerformed(false);
            evidence.setComparisonReason("PHASH_UNAVAILABLE");
        }
    }

    private void captureComparisonEvidence(
            DigitalAsset asset,
            VerificationEvidence evidence) {
        try {
            KnownOriginalComparisonResponse response =
                    comparisonService.compareWithKnownOriginal(asset.getUuid());
            evidence.setComparisonPerformed(response.isComparisonPerformed());
            evidence.setComparisonReason(response.getReason());
            KnownOriginalComparisonResult result = response.getComparison();
            if (result != null) {
                evidence.setComparisonStatus(result.getStatus());
                evidence.setChangedAreaRatio(result.getChangedAreaRatio());
                evidence.setMeanAbsoluteDifference(result.getMeanAbsoluteDifference());
                evidence.setStructuralSimilarity(result.getStructuralSimilarity());
            }
        } catch (RuntimeException exception) {
            evidence.setComparisonPerformed(false);
            evidence.setComparisonReason("COMPARISON_UNAVAILABLE");
        }
    }

    private void captureFabricEvidence(
            DigitalAsset asset,
            VerificationEvidence evidence) {
        try {
            BlockchainOriginalLookupResponse lookup =
                    blockchainRegistryService.findBySha256(asset.getSha256Hash());
            evidence.setFabricLookupPerformed(true);
            evidence.setFabricStatus("COMPLETED");
            evidence.setFabricRegisteredOriginalFound(lookup.found());
            if (lookup.found() && lookup.asset() != null) {
                evidence.setFabricReferenceAssetUuid(lookup.asset().assetId());
            }
        } catch (BlockchainException exception) {
            evidence.setFabricLookupPerformed(false);
            evidence.setFabricStatus(exception.getCode());
            evidence.setFabricRegisteredOriginalFound(null);
        } catch (RuntimeException exception) {
            evidence.setFabricLookupPerformed(false);
            evidence.setFabricStatus("BLOCKCHAIN_UNAVAILABLE");
            evidence.setFabricRegisteredOriginalFound(null);
        }
    }

    String hashEvidence(VerificationEvidence evidence) {
        String canonical = String.join("\n",
                EVIDENCE_VERSION,
                value(evidence.getAsset().getUuid()),
                value(evidence.getSha256()),
                value(evidence.isExactDuplicateDetected()),
                value(evidence.getPerceptualHash()),
                value(evidence.getPerceptualHashStatus()),
                value(evidence.getSimilarCandidateCount()),
                value(evidence.getBestCandidateAssetUuid()),
                value(evidence.getBestPhashDistance()),
                value(evidence.isComparisonPerformed()),
                value(evidence.getComparisonStatus()),
                value(evidence.getComparisonReason()),
                value(evidence.getChangedAreaRatio()),
                value(evidence.getMeanAbsoluteDifference()),
                value(evidence.getStructuralSimilarity()),
                value(evidence.isFabricLookupPerformed()),
                value(evidence.getFabricStatus()),
                value(evidence.getFabricRegisteredOriginalFound()),
                value(evidence.getFabricReferenceAssetUuid()),
                value(evidence.getGeneratedAt()));
        try {
            return sha256Service.calculate(new ByteArrayInputStream(
                    canonical.getBytes(StandardCharsets.UTF_8))).hash();
        } catch (IOException exception) {
            throw new VerificationException("Could not fingerprint verification evidence", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    private DigitalAsset findOwnedAsset(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        return digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private VerificationEvidence latestEvidence(DigitalAsset asset) {
        return evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Verification evidence has not been generated"));
    }
}
