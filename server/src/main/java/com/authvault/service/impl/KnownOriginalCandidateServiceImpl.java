package com.authvault.service.impl;

import com.authvault.config.PerceptualHashProperties;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.asset.SimilarImageMatchResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.exception.VerificationException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetStorageService;
import com.authvault.service.KnownOriginalCandidateService;
import com.authvault.service.PerceptualHashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

@Service
public class KnownOriginalCandidateServiceImpl implements KnownOriginalCandidateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnownOriginalCandidateServiceImpl.class);

    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetStorageService assetStorageService;
    private final PerceptualHashService perceptualHashService;
    private final PerceptualHashProperties properties;

    public KnownOriginalCandidateServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            AssetStorageService assetStorageService,
            PerceptualHashService perceptualHashService,
            PerceptualHashProperties properties) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.assetStorageService = assetStorageService;
        this.perceptualHashService = perceptualHashService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public SimilarImagesResponse findSimilarImages(String assetId) {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Perceptual image matching is disabled");
        }

        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset target = digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        requireImage(target);

        String targetHash = ensureTargetHash(target);
        List<SimilarImageMatchResponse> matches = digitalAssetRepository.findPreviousOwnedImages(
                currentUser, target.getUuid(), target.getUploadDate())
                .stream()
                .map(candidate -> toMatchSafely(candidate, target, targetHash))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator
                        .comparingInt(SimilarImageMatchResponse::getHammingDistance)
                        .thenComparing(SimilarImageMatchResponse::getUploadedAt,
                                Comparator.reverseOrder())
                        .thenComparing(SimilarImageMatchResponse::getMatchedAssetId))
                .limit(properties.getMaxCandidates())
                .toList();

        return SimilarImagesResponse.builder()
                .assetId(target.getUuid())
                .perceptualHash(targetHash)
                .closestMatches(matches)
                .build();
    }

    private void requireImage(DigitalAsset asset) {
        if (asset.getAssetType() != DigitalAsset.AssetType.IMAGE) {
            throw new BadRequestException("Perceptual matching is available only for image assets");
        }
    }

    private String ensureTargetHash(DigitalAsset target) {
        if (target.getPerceptualHash() != null) {
            if (!perceptualHashService.isValidHash(target.getPerceptualHash())) {
                throw new VerificationException("Stored perceptual hash is invalid");
            }
            return target.getPerceptualHash();
        }

        try (InputStream inputStream = assetStorageService.loadStoredAsset(target.getStoragePath())) {
            String hash = perceptualHashService.calculate(inputStream);
            if (!perceptualHashService.isValidHash(hash)) {
                throw new VerificationException("Calculated perceptual hash has an invalid format");
            }
            target.setPerceptualHash(hash);
            digitalAssetRepository.save(target);
            return hash;
        } catch (RuntimeException exception) {
            throw new VerificationException("Could not calculate perceptual hash for target image", exception);
        } catch (Exception exception) {
            throw new VerificationException("Could not read target image for perceptual hashing", exception);
        }
    }

    private java.util.Optional<SimilarImageMatchResponse> toMatchSafely(
            DigitalAsset candidate,
            DigitalAsset target,
            String targetHash) {
        try {
            if (candidate.getAssetType() != DigitalAsset.AssetType.IMAGE
                    || candidate.getUuid().equals(target.getUuid())
                    || !candidate.getUploadDate().isBefore(target.getUploadDate())) {
                return java.util.Optional.empty();
            }

            String candidateHash = ensureCandidateHash(candidate);
            int distance = perceptualHashService.hammingDistance(
                    candidateHash,
                    targetHash);
            return java.util.Optional.of(SimilarImageMatchResponse.builder()
                    .matchedAssetId(candidate.getUuid())
                    .title(candidate.getTitle())
                    .originalFilename(candidate.getOriginalFilename())
                    .uploadedAt(candidate.getUploadDate())
                    .hammingDistance(distance)
                    .matchBand(matchBand(distance))
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping an image candidate because its perceptual hash is unavailable or invalid");
            return java.util.Optional.empty();
        }
    }

    private String ensureCandidateHash(DigitalAsset candidate) {
        if (candidate.getPerceptualHash() != null) {
            if (!perceptualHashService.isValidHash(candidate.getPerceptualHash())) {
                throw new VerificationException("Stored candidate perceptual hash is invalid");
            }
            return candidate.getPerceptualHash();
        }

        try (InputStream inputStream = assetStorageService.loadStoredAsset(candidate.getStoragePath())) {
            String hash = perceptualHashService.calculate(inputStream);
            if (!perceptualHashService.isValidHash(hash)) {
                throw new VerificationException("Calculated candidate perceptual hash has an invalid format");
            }
            candidate.setPerceptualHash(hash);
            digitalAssetRepository.save(candidate);
            return hash;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VerificationException("Could not read candidate image for perceptual hashing", exception);
        }
    }

    private PerceptualMatchBand matchBand(int distance) {
        if (distance == 0) {
            return PerceptualMatchBand.EXACT_VISUAL_HASH;
        }
        if (distance <= properties.getReviewThreshold()) {
            return PerceptualMatchBand.NEAR_DUPLICATE;
        }
        if (distance <= properties.getPossibleMatchThreshold()) {
            return PerceptualMatchBand.POSSIBLE_MATCH;
        }
        return PerceptualMatchBand.NO_MATCH;
    }
}
