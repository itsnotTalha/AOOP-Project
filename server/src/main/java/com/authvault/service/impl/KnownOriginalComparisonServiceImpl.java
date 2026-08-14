package com.authvault.service.impl;

import com.authvault.client.ai.AiClientResult;
import com.authvault.client.ai.AiForensicsClient;
import com.authvault.dto.ai.AiImageComparisonResponse;
import com.authvault.dto.asset.KnownOriginalCandidateResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResult;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.asset.SimilarImageMatchResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetStorageService;
import com.authvault.service.KnownOriginalCandidateService;
import com.authvault.service.KnownOriginalComparisonService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class KnownOriginalComparisonServiceImpl implements KnownOriginalComparisonService {

    private static final String NO_KNOWN_ORIGINAL = "NO_KNOWN_ORIGINAL";
    private static final String AI_SERVICE_DISABLED = "AI_SERVICE_DISABLED";
    private static final String AI_SERVICE_UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";

    private final DigitalAssetRepository digitalAssetRepository;
    private final KnownOriginalCandidateService candidateService;
    private final AssetStorageService assetStorageService;
    private final AiForensicsClient aiForensicsClient;

    public KnownOriginalComparisonServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            KnownOriginalCandidateService candidateService,
            AssetStorageService assetStorageService,
            AiForensicsClient aiForensicsClient) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.candidateService = candidateService;
        this.assetStorageService = assetStorageService;
        this.aiForensicsClient = aiForensicsClient;
    }

    @Override
    public KnownOriginalComparisonResponse compareWithKnownOriginal(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset target = digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        requireImage(target);

        SimilarImagesResponse candidateSearch = candidateService.findSimilarImages(assetId);
        Optional<ResolvedCandidate> resolvedCandidate = candidateSearch.getClosestMatches().stream()
                .filter(this::isPlausible)
                .map(match -> resolveOwnedCandidate(match, currentUser))
                .flatMap(Optional::stream)
                .findFirst();
        if (resolvedCandidate.isEmpty()) {
            return noCandidateResponse(target.getUuid());
        }

        ResolvedCandidate selected = resolvedCandidate.get();
        KnownOriginalCandidateResponse publicCandidate = toPublicCandidate(selected.match());
        try (InputStream referenceInput = assetStorageService.loadStoredAsset(
                selected.asset().getStoragePath());
             InputStream targetInput = assetStorageService.loadStoredAsset(target.getStoragePath())) {
            AiClientResult<AiImageComparisonResponse> clientResult = aiForensicsClient.compareImages(
                    resource(referenceInput, "reference-image"),
                    safeImageMediaType(selected.asset().getMimeType()),
                    resource(targetInput, "target-image"),
                    safeImageMediaType(target.getMimeType()));
            return mapClientResult(target.getUuid(), publicCandidate, clientResult);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new com.authvault.exception.VerificationException(
                    "Could not compare image with known original", exception);
        }
    }

    private void requireImage(DigitalAsset asset) {
        if (asset.getAssetType() != DigitalAsset.AssetType.IMAGE) {
            throw new BadRequestException("Known-original comparison is available only for image assets");
        }
    }

    private boolean isPlausible(SimilarImageMatchResponse match) {
        return match.getMatchBand() == PerceptualMatchBand.EXACT_VISUAL_HASH
                || match.getMatchBand() == PerceptualMatchBand.NEAR_DUPLICATE
                || match.getMatchBand() == PerceptualMatchBand.POSSIBLE_MATCH;
    }

    private Optional<ResolvedCandidate> resolveOwnedCandidate(
            SimilarImageMatchResponse match,
            User currentUser) {
        return digitalAssetRepository
                .findByUuidAndCurrentOwner(match.getMatchedAssetId(), currentUser)
                .filter(asset -> asset.getAssetType() == DigitalAsset.AssetType.IMAGE)
                .map(asset -> new ResolvedCandidate(asset, match));
    }

    private KnownOriginalComparisonResponse mapClientResult(
            String targetAssetId,
            KnownOriginalCandidateResponse candidate,
            AiClientResult<AiImageComparisonResponse> clientResult) {
        return switch (clientResult.status()) {
            case DISABLED -> unavailableResponse(
                    targetAssetId, candidate, AI_SERVICE_DISABLED);
            case UNAVAILABLE -> unavailableResponse(
                    targetAssetId, candidate, AI_SERVICE_UNAVAILABLE);
            case SUCCESS -> completedResponse(
                    targetAssetId, candidate, clientResult.body());
        };
    }

    private KnownOriginalComparisonResponse completedResponse(
            String targetAssetId,
            KnownOriginalCandidateResponse candidate,
            AiImageComparisonResponse internal) {
        if (internal == null
                || internal.alignment() == null
                || internal.difference() == null
                || internal.mask() == null) {
            return unavailableResponse(targetAssetId, candidate, AI_SERVICE_UNAVAILABLE);
        }

        AiImageComparisonResponse.Alignment alignment = internal.alignment();
        AiImageComparisonResponse.Difference difference = internal.difference();
        AiImageComparisonResponse.ChangeMask mask = internal.mask();
        KnownOriginalComparisonResult comparison = KnownOriginalComparisonResult.builder()
                .status(internal.status())
                .alignment(KnownOriginalComparisonResult.Alignment.builder()
                        .status(alignment.status())
                        .method(alignment.method())
                        .keypointsReference(alignment.keypointsReference())
                        .keypointsTarget(alignment.keypointsTarget())
                        .goodMatches(alignment.goodMatches())
                        .inliers(alignment.inliers())
                        .inlierRatio(alignment.inlierRatio())
                        .build())
                .differencePerformed(difference.performed())
                .changedAreaRatio(difference.changedAreaRatio())
                .meanAbsoluteDifference(difference.meanAbsoluteDifference())
                .structuralSimilarity(difference.structuralSimilarity())
                .changeMaskPngBase64(mask.available() ? mask.base64() : null)
                .build();

        return KnownOriginalComparisonResponse.builder()
                .assetId(targetAssetId)
                .candidateFound(true)
                .candidate(candidate)
                .comparisonPerformed(true)
                .reason(comparisonReason(internal.status()))
                .comparison(comparison)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private String comparisonReason(String status) {
        if ("NO_VALID_OVERLAP".equals(status)) {
            return "NO_VALID_OVERLAP";
        }
        if ("PROCESSING_FAILED".equals(status)) {
            return "COMPARISON_PROCESSING_FAILED";
        }
        return null;
    }

    private KnownOriginalComparisonResponse noCandidateResponse(String targetAssetId) {
        return KnownOriginalComparisonResponse.builder()
                .assetId(targetAssetId)
                .candidateFound(false)
                .comparisonPerformed(false)
                .reason(NO_KNOWN_ORIGINAL)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private KnownOriginalComparisonResponse unavailableResponse(
            String targetAssetId,
            KnownOriginalCandidateResponse candidate,
            String reason) {
        return KnownOriginalComparisonResponse.builder()
                .assetId(targetAssetId)
                .candidateFound(true)
                .candidate(candidate)
                .comparisonPerformed(false)
                .reason(reason)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private KnownOriginalCandidateResponse toPublicCandidate(SimilarImageMatchResponse match) {
        return KnownOriginalCandidateResponse.builder()
                .assetId(match.getMatchedAssetId())
                .title(match.getTitle())
                .originalFilename(match.getOriginalFilename())
                .hammingDistance(match.getHammingDistance())
                .matchBand(match.getMatchBand())
                .build();
    }

    private Resource resource(InputStream inputStream, String description) {
        return new InputStreamResource(inputStream) {
            @Override
            public String getFilename() {
                return description;
            }

            @Override
            public long contentLength() {
                return -1L;
            }
        };
    }

    private MediaType safeImageMediaType(String mimeType) {
        try {
            MediaType parsed = MediaType.parseMediaType(mimeType);
            if (MediaType.IMAGE_JPEG.equals(parsed) || MediaType.IMAGE_PNG.equals(parsed)) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // The AI service validates actual bytes; do not expose stored metadata failures.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private record ResolvedCandidate(
            DigitalAsset asset,
            SimilarImageMatchResponse match) {
    }
}
