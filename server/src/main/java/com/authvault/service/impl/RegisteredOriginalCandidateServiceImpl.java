package com.authvault.service.impl;

import com.authvault.config.PerceptualHashProperties;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.entity.DigitalAsset;
import com.authvault.exception.BadRequestException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.service.PerceptualHashService;
import com.authvault.service.RegisteredOriginalCandidateService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class RegisteredOriginalCandidateServiceImpl
        implements RegisteredOriginalCandidateService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private final DigitalAssetRepository digitalAssetRepository;
    private final PerceptualHashService perceptualHashService;
    private final PerceptualHashProperties properties;

    public RegisteredOriginalCandidateServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            PerceptualHashService perceptualHashService,
            PerceptualHashProperties properties) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.perceptualHashService = perceptualHashService;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> findPlausibleCandidates(String submittedPerceptualHash) {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Perceptual image matching is disabled");
        }
        if (!perceptualHashService.isValidHash(submittedPerceptualHash)) {
            throw new IllegalArgumentException(
                    "Submitted perceptual hash must be 16 lowercase hexadecimal characters");
        }

        return digitalAssetRepository.findVerifiedImageRegistryCandidates(
                        PageRequest.of(0, properties.getRegistryScanLimit()))
                .stream()
                .map(asset -> toPlausibleCandidate(asset, submittedPerceptualHash))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingInt(Candidate::pHashDistance)
                        .thenComparing(Candidate::assetId))
                .limit(properties.getMaxCandidates())
                .toList();
    }

    private Optional<Candidate> toPlausibleCandidate(
            DigitalAsset asset,
            String submittedPerceptualHash) {
        if (asset == null
                || asset.getAssetType() != DigitalAsset.AssetType.IMAGE
                || asset.getVerificationStatus() != DigitalAsset.VerificationStatus.VERIFIED
                || asset.getUuid() == null
                || asset.getSha256Hash() == null
                || !SHA256_PATTERN.matcher(asset.getSha256Hash()).matches()
                || asset.getStoragePath() == null
                || !perceptualHashService.isValidHash(asset.getPerceptualHash())) {
            return Optional.empty();
        }

        int distance;
        try {
            distance = perceptualHashService.hammingDistance(
                    submittedPerceptualHash, asset.getPerceptualHash());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        PerceptualMatchBand band = matchBand(distance);
        if (band == PerceptualMatchBand.NO_MATCH) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(
                asset.getUuid(),
                asset.getSha256Hash(),
                asset.getStoragePath(),
                distance,
                band));
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
