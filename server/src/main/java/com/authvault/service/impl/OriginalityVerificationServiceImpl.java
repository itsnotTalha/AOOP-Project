package com.authvault.service.impl;

import com.authvault.blockchain.OriginalRegistryClient;
import com.authvault.blockchain.OriginalRegistryClientException;
import com.authvault.client.comparison.ComparisonClientResult;
import com.authvault.client.comparison.ImageComparisonClient;
import com.authvault.dto.comparison.ImageComparisonResponse;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.originality.OriginalityOutcome;
import com.authvault.dto.originality.OriginalityReasonCode;
import com.authvault.dto.originality.OriginalityVerificationResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.exception.FileStorageException;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetStorageService;
import com.authvault.service.OriginalityVerificationService;
import com.authvault.service.PerceptualHashService;
import com.authvault.service.RegisteredOriginalCandidateService;
import com.authvault.service.Sha256Service;
import com.authvault.validation.PreparedUploadFile;
import com.authvault.validation.UploadFileValidator;
import com.authvault.validation.ValidatedUploadFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OriginalityVerificationServiceImpl implements OriginalityVerificationService {

    private static final String BLOCKCHAIN_COMPLETED = "COMPLETED";
    private static final String BLOCKCHAIN_DISABLED = "DISABLED";
    private static final String BLOCKCHAIN_UNAVAILABLE = "UNAVAILABLE";
    private static final String BLOCKCHAIN_INVALID_RESPONSE = "INVALID_RESPONSE";
    private static final String AI_EVIDENCE_DEFERRED = "DEFERRED";
    private static final String COMPARISON_UNAVAILABLE = "UNAVAILABLE";
    private static final String CONTENT_UNAVAILABLE =
            "REGISTERED_ORIGINAL_CONTENT_UNAVAILABLE";

    private final UploadFileValidator uploadFileValidator;
    private final AssetStorageService assetStorageService;
    private final Sha256Service sha256Service;
    private final PerceptualHashService perceptualHashService;
    private final OriginalRegistryClient registryClient;
    private final RegisteredOriginalCandidateService candidateService;
    private final ImageComparisonClient imageComparisonClient;

    public OriginalityVerificationServiceImpl(
            UploadFileValidator uploadFileValidator,
            AssetStorageService assetStorageService,
            Sha256Service sha256Service,
            PerceptualHashService perceptualHashService,
            OriginalRegistryClient registryClient,
            RegisteredOriginalCandidateService candidateService,
            ImageComparisonClient imageComparisonClient) {
        this.uploadFileValidator = uploadFileValidator;
        this.assetStorageService = assetStorageService;
        this.sha256Service = sha256Service;
        this.perceptualHashService = perceptualHashService;
        this.registryClient = registryClient;
        this.candidateService = candidateService;
        this.imageComparisonClient = imageComparisonClient;
    }

    @Override
    public OriginalityVerificationResponse verify(MultipartFile image) {
        SecurityUtils.getCurrentUser();
        PreparedUploadFile preparedFile = uploadFileValidator.prepare(
                image, DigitalAsset.AssetType.IMAGE);
        Path submittedTempFile = null;
        try {
            submittedTempFile = assetStorageService.createTempFile(preparedFile.extension());
            Sha256Service.Sha256Result submittedHash = copySubmittedImage(
                    image, submittedTempFile, preparedFile.maxFileSizeBytes());
            ValidatedUploadFile submittedFile = uploadFileValidator.validateTempFile(
                    submittedTempFile, preparedFile, submittedHash.bytesRead());

            if (!registryClient.isEnabled()) {
                return inconclusiveBlockchain(
                        submittedHash.hash(), null, BLOCKCHAIN_DISABLED,
                        OriginalityReasonCode.BLOCKCHAIN_DISABLED);
            }

            RegistryLookup exactLookup = lookupRegistry(submittedHash.hash());
            if (exactLookup.failure() != null) {
                return inconclusiveBlockchain(
                        submittedHash.hash(), null,
                        exactLookup.failure().status(), exactLookup.failure().reasonCode());
            }
            if (exactLookup.original().isPresent()) {
                BlockchainOriginalResponse exact = exactLookup.original().get();
                if (!isConsistentImageOriginal(exact, exact.assetId(), submittedHash.hash())) {
                    return inconclusiveBlockchain(
                            submittedHash.hash(), null,
                            BLOCKCHAIN_INVALID_RESPONSE,
                            OriginalityReasonCode.BLOCKCHAIN_INVALID_RESPONSE);
                }
                return response(
                        OriginalityOutcome.EXACT_REGISTERED_ORIGINAL,
                        submittedHash.hash(),
                        null,
                        new OriginalityVerificationResponse.BlockchainLookup(
                                BLOCKCHAIN_COMPLETED, true),
                        toRegisteredOriginal(exact),
                        null,
                        null,
                        List.of(OriginalityReasonCode.EXACT_SHA256_MATCH));
            }

            String submittedPerceptualHash;
            try (InputStream inputStream = Files.newInputStream(submittedTempFile)) {
                submittedPerceptualHash = perceptualHashService.calculate(inputStream);
            } catch (IOException | RuntimeException exception) {
                return response(
                        OriginalityOutcome.INCONCLUSIVE,
                        submittedHash.hash(),
                        null,
                        completedMiss(),
                        null,
                        null,
                        null,
                        List.of(
                                OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                                OriginalityReasonCode.PHASH_UNAVAILABLE));
            }

            List<RegisteredOriginalCandidateService.Candidate> candidates;
            try {
                candidates = candidateService.findPlausibleCandidates(
                        submittedPerceptualHash);
            } catch (RuntimeException exception) {
                return response(
                        OriginalityOutcome.INCONCLUSIVE,
                        submittedHash.hash(),
                        submittedPerceptualHash,
                        completedMiss(),
                        null,
                        null,
                        null,
                        List.of(
                                OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                                OriginalityReasonCode.PHASH_UNAVAILABLE));
            }

            CandidateConfirmation confirmation = confirmCandidate(candidates);
            if (confirmation.failure() != null) {
                return response(
                        OriginalityOutcome.INCONCLUSIVE,
                        submittedHash.hash(),
                        submittedPerceptualHash,
                        new OriginalityVerificationResponse.BlockchainLookup(
                                confirmation.failure().status(), false),
                        null,
                        null,
                        null,
                        List.of(
                                OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                                confirmation.failure().reasonCode()));
            }
            if (confirmation.confirmed() == null) {
                return response(
                        OriginalityOutcome.NO_REGISTERED_SOURCE_FOUND,
                        submittedHash.hash(),
                        submittedPerceptualHash,
                        completedMiss(),
                        null,
                        null,
                        null,
                        List.of(
                                OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                                OriginalityReasonCode.NO_PLAUSIBLE_REGISTERED_SOURCE));
            }

            ConfirmedCandidate confirmed = confirmation.confirmed();
            return compareWithConfirmedOriginal(
                    submittedTempFile,
                    submittedFile,
                    submittedHash.hash(),
                    submittedPerceptualHash,
                    confirmed);
        } finally {
            assetStorageService.deleteTempQuietly(submittedTempFile);
        }
    }

    private OriginalityVerificationResponse compareWithConfirmedOriginal(
            Path submittedTempFile,
            ValidatedUploadFile submittedFile,
            String submittedSha256,
            String submittedPerceptualHash,
            ConfirmedCandidate confirmed) {
        Path referenceTempFile = null;
        try {
            referenceTempFile = assetStorageService.createTempFile("png");
            ValidatedUploadFile referenceFile = copyAndValidateRegisteredOriginal(
                    confirmed, referenceTempFile);
            if (referenceFile == null) {
                return contentUnavailableResponse(
                        submittedSha256, submittedPerceptualHash, confirmed);
            }

            ComparisonClientResult<ImageComparisonResponse> clientResult;
            try {
                clientResult = imageComparisonClient.compareImages(
                        new FileSystemResource(referenceTempFile),
                        MediaType.parseMediaType(referenceFile.mimeType()),
                        new FileSystemResource(submittedTempFile),
                        MediaType.parseMediaType(submittedFile.mimeType()));
            } catch (RuntimeException exception) {
                return comparisonUnavailableResponse(
                        submittedSha256, submittedPerceptualHash, confirmed);
            }
            if (clientResult == null
                    || clientResult.status() != ComparisonClientResult.Status.SUCCESS) {
                return comparisonUnavailableResponse(
                        submittedSha256, submittedPerceptualHash, confirmed);
            }
            return classifyComparison(
                    submittedSha256,
                    submittedPerceptualHash,
                    confirmed,
                    clientResult.body());
        } finally {
            assetStorageService.deleteTempQuietly(referenceTempFile);
        }
    }

    private ValidatedUploadFile copyAndValidateRegisteredOriginal(
            ConfirmedCandidate confirmed,
            Path referenceTempFile) {
        try (InputStream inputStream = assetStorageService.loadStoredAsset(
                confirmed.candidate().storageKey());
             OutputStream outputStream = Files.newOutputStream(referenceTempFile)) {
            Sha256Service.Sha256Result hash = sha256Service.copyAndCalculate(
                    inputStream,
                    outputStream,
                    uploadFileValidator.imageMaxSizeBytes());
            if (!hash.hash().equals(confirmed.candidate().sha256())
                    || !hash.hash().equals(confirmed.original().sha256())) {
                return null;
            }
            return uploadFileValidator.validateControlledTempImage(
                    referenceTempFile, hash.bytesRead());
        } catch (Exception exception) {
            return null;
        }
    }

    private OriginalityVerificationResponse classifyComparison(
            String submittedSha256,
            String submittedPerceptualHash,
            ConfirmedCandidate confirmed,
            ImageComparisonResponse comparison) {
        if (!isUsableComparison(comparison)) {
            return comparisonUnavailableResponse(
                    submittedSha256, submittedPerceptualHash, confirmed);
        }

        OriginalityVerificationResponse.Comparison publicComparison =
                toPublicComparison(comparison);
        boolean strongSimilarity = confirmed.candidate().matchBand()
                == PerceptualMatchBand.EXACT_VISUAL_HASH
                || confirmed.candidate().matchBand() == PerceptualMatchBand.NEAR_DUPLICATE;
        boolean reliableAlignment = "ALIGNED".equals(comparison.alignment().status());
        boolean meaningfulChanges = comparison.difference().changedAreaRatio() > 0.0;

        List<OriginalityReasonCode> reasons = baseCandidateReasons(confirmed.candidate());
        OriginalityOutcome outcome;
        if (strongSimilarity && reliableAlignment && meaningfulChanges) {
            outcome = OriginalityOutcome.MODIFIED_REGISTERED_ORIGINAL;
            reasons.add(OriginalityReasonCode.MEANINGFUL_CHANGES_DETECTED);
        } else {
            outcome = OriginalityOutcome.POSSIBLE_DERIVATIVE;
            reasons.add(OriginalityReasonCode.COMPARISON_LOW_RELIABILITY);
        }

        return response(
                outcome,
                submittedSha256,
                submittedPerceptualHash,
                completedMiss(),
                toRegisteredOriginal(confirmed.original()),
                toSimilarity(confirmed.candidate()),
                publicComparison,
                reasons);
    }

    private boolean isUsableComparison(ImageComparisonResponse comparison) {
        if (comparison == null
                || !"COMPLETED".equals(comparison.status())
                || comparison.alignment() == null
                || comparison.difference() == null
                || !comparison.difference().performed()
                || comparison.difference().changedAreaRatio() == null) {
            return false;
        }
        double changedAreaRatio = comparison.difference().changedAreaRatio();
        return Double.isFinite(changedAreaRatio)
                && changedAreaRatio >= 0.0
                && changedAreaRatio <= 1.0
                && isFiniteOrNull(comparison.difference().meanAbsoluteDifference())
                && isFiniteOrNull(comparison.difference().structuralSimilarity())
                && isFiniteOrNull(comparison.alignment().inlierRatio());
    }

    private boolean isFiniteOrNull(Double value) {
        return value == null || Double.isFinite(value);
    }

    private OriginalityVerificationResponse.Comparison toPublicComparison(
            ImageComparisonResponse internal) {
        ImageComparisonResponse.Alignment alignment = internal.alignment();
        ImageComparisonResponse.Difference difference = internal.difference();
        ImageComparisonResponse.ChangeMask mask = internal.mask();
        return new OriginalityVerificationResponse.Comparison(
                internal.status(),
                new OriginalityVerificationResponse.Alignment(
                        alignment.status(),
                        alignment.method(),
                        alignment.keypointsReference(),
                        alignment.keypointsTarget(),
                        alignment.goodMatches(),
                        alignment.inliers(),
                        alignment.inlierRatio()),
                difference.performed(),
                difference.changedAreaRatio(),
                difference.meanAbsoluteDifference(),
                difference.structuralSimilarity(),
                mask != null && mask.available() ? mask.base64() : null);
    }

    private CandidateConfirmation confirmCandidate(
            List<RegisteredOriginalCandidateService.Candidate> candidates) {
        for (RegisteredOriginalCandidateService.Candidate candidate : candidates) {
            RegistryLookup lookup = lookupRegistry(candidate.sha256());
            if (lookup.failure() != null) {
                return new CandidateConfirmation(null, lookup.failure());
            }
            if (lookup.original().isEmpty()) {
                continue;
            }
            BlockchainOriginalResponse original = lookup.original().get();
            if (!isConsistentImageOriginal(original, candidate.assetId(), candidate.sha256())) {
                return new CandidateConfirmation(
                        null,
                        new RegistryFailure(
                                BLOCKCHAIN_INVALID_RESPONSE,
                                OriginalityReasonCode.BLOCKCHAIN_INVALID_RESPONSE));
            }
            return new CandidateConfirmation(
                    new ConfirmedCandidate(candidate, original), null);
        }
        return new CandidateConfirmation(null, null);
    }

    private RegistryLookup lookupRegistry(String sha256) {
        try {
            return new RegistryLookup(registryClient.findBySha256(sha256), null);
        } catch (OriginalRegistryClientException exception) {
            return new RegistryLookup(Optional.empty(), registryFailure(exception));
        } catch (RuntimeException exception) {
            return new RegistryLookup(
                    Optional.empty(),
                    new RegistryFailure(
                            BLOCKCHAIN_UNAVAILABLE,
                            OriginalityReasonCode.BLOCKCHAIN_UNAVAILABLE));
        }
    }

    private RegistryFailure registryFailure(OriginalRegistryClientException exception) {
        return switch (exception.getReason()) {
            case DISABLED -> new RegistryFailure(
                    BLOCKCHAIN_DISABLED, OriginalityReasonCode.BLOCKCHAIN_DISABLED);
            case INVALID_RESPONSE -> new RegistryFailure(
                    BLOCKCHAIN_INVALID_RESPONSE,
                    OriginalityReasonCode.BLOCKCHAIN_INVALID_RESPONSE);
            default -> new RegistryFailure(
                    BLOCKCHAIN_UNAVAILABLE,
                    OriginalityReasonCode.BLOCKCHAIN_UNAVAILABLE);
        };
    }

    private boolean isConsistentImageOriginal(
            BlockchainOriginalResponse original,
            String expectedAssetId,
            String expectedSha256) {
        return original != null
                && expectedAssetId != null
                && expectedSha256 != null
                && expectedAssetId.equals(original.assetId())
                && expectedSha256.equals(original.sha256())
                && "IMAGE".equals(original.assetType())
                && "VERIFIED".equals(original.verificationStatus())
                && original.creatorIdHash() != null
                && !original.creatorIdHash().isBlank()
                && original.evidenceHash() != null
                && original.registeredAt() != null;
    }

    private Sha256Service.Sha256Result copySubmittedImage(
            MultipartFile image,
            Path temporaryFile,
            long maxBytes) {
        try (InputStream inputStream = image.getInputStream();
             OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
            Sha256Service.Sha256Result result = sha256Service.copyAndCalculate(
                    inputStream, outputStream, maxBytes);
            if (result.bytesRead() == 0) {
                throw new BadRequestException("Uploaded image is required and must not be empty");
            }
            return result;
        } catch (FileSizeLimitExceededException exception) {
            throw new FileSizeLimitExceededException(
                    "IMAGE file exceeds the configured maximum size of " + maxBytes + " bytes");
        } catch (IOException exception) {
            throw new FileStorageException("Could not process originality image", exception);
        }
    }

    private OriginalityVerificationResponse contentUnavailableResponse(
            String submittedSha256,
            String submittedPerceptualHash,
            ConfirmedCandidate confirmed) {
        List<OriginalityReasonCode> reasons = baseCandidateReasons(confirmed.candidate());
        reasons.add(OriginalityReasonCode.REGISTERED_ORIGINAL_CONTENT_UNAVAILABLE);
        return response(
                OriginalityOutcome.INCONCLUSIVE,
                submittedSha256,
                submittedPerceptualHash,
                completedMiss(),
                toRegisteredOriginal(confirmed.original()),
                toSimilarity(confirmed.candidate()),
                new OriginalityVerificationResponse.Comparison(
                        CONTENT_UNAVAILABLE, null, null, null, null, null, null),
                reasons);
    }

    private OriginalityVerificationResponse comparisonUnavailableResponse(
            String submittedSha256,
            String submittedPerceptualHash,
            ConfirmedCandidate confirmed) {
        List<OriginalityReasonCode> reasons = baseCandidateReasons(confirmed.candidate());
        reasons.add(OriginalityReasonCode.COMPARISON_UNAVAILABLE);
        return response(
                OriginalityOutcome.INCONCLUSIVE,
                submittedSha256,
                submittedPerceptualHash,
                completedMiss(),
                toRegisteredOriginal(confirmed.original()),
                toSimilarity(confirmed.candidate()),
                new OriginalityVerificationResponse.Comparison(
                        COMPARISON_UNAVAILABLE, null, null, null, null, null, null),
                reasons);
    }

    private List<OriginalityReasonCode> baseCandidateReasons(
            RegisteredOriginalCandidateService.Candidate candidate) {
        List<OriginalityReasonCode> reasons = new ArrayList<>();
        reasons.add(OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND);
        reasons.add(OriginalityReasonCode.REGISTERED_SOURCE_FOUND);
        reasons.add(OriginalityReasonCode.SHA256_DIFFERENT);
        reasons.add(switch (candidate.matchBand()) {
            case EXACT_VISUAL_HASH -> OriginalityReasonCode.PHASH_EXACT_VISUAL_HASH;
            case NEAR_DUPLICATE -> OriginalityReasonCode.PHASH_NEAR_DUPLICATE;
            case POSSIBLE_MATCH -> OriginalityReasonCode.PHASH_POSSIBLE_MATCH;
            case NO_MATCH -> throw new IllegalArgumentException(
                    "NO_MATCH cannot be used as a registered-original candidate");
        });
        return reasons;
    }

    private OriginalityVerificationResponse inconclusiveBlockchain(
            String submittedSha256,
            String submittedPerceptualHash,
            String blockchainStatus,
            OriginalityReasonCode reasonCode) {
        return response(
                OriginalityOutcome.INCONCLUSIVE,
                submittedSha256,
                submittedPerceptualHash,
                new OriginalityVerificationResponse.BlockchainLookup(
                        blockchainStatus, false),
                null,
                null,
                null,
                List.of(reasonCode));
    }

    private OriginalityVerificationResponse response(
            OriginalityOutcome outcome,
            String submittedSha256,
            String submittedPerceptualHash,
            OriginalityVerificationResponse.BlockchainLookup blockchainLookup,
            OriginalityVerificationResponse.RegisteredOriginal registeredOriginal,
            OriginalityVerificationResponse.Similarity similarity,
            OriginalityVerificationResponse.Comparison comparison,
            List<OriginalityReasonCode> reasonCodes) {
        return new OriginalityVerificationResponse(
                outcome,
                new OriginalityVerificationResponse.Submitted(
                        submittedSha256, submittedPerceptualHash),
                blockchainLookup,
                registeredOriginal,
                similarity,
                comparison,
                new OriginalityVerificationResponse.AiEvidence(AI_EVIDENCE_DEFERRED),
                List.copyOf(reasonCodes),
                LocalDateTime.now());
    }

    private OriginalityVerificationResponse.BlockchainLookup completedMiss() {
        return new OriginalityVerificationResponse.BlockchainLookup(
                BLOCKCHAIN_COMPLETED, false);
    }

    private OriginalityVerificationResponse.RegisteredOriginal toRegisteredOriginal(
            BlockchainOriginalResponse original) {
        return new OriginalityVerificationResponse.RegisteredOriginal(
                original.assetId(),
                original.creatorIdHash(),
                original.sha256(),
                original.registeredAt(),
                original.assetType(),
                original.verificationStatus(),
                original.evidenceHash());
    }

    private OriginalityVerificationResponse.Similarity toSimilarity(
            RegisteredOriginalCandidateService.Candidate candidate) {
        return new OriginalityVerificationResponse.Similarity(
                candidate.pHashDistance(), candidate.matchBand());
    }

    private record RegistryFailure(
            String status,
            OriginalityReasonCode reasonCode) {
    }

    private record RegistryLookup(
            Optional<BlockchainOriginalResponse> original,
            RegistryFailure failure) {
    }

    private record ConfirmedCandidate(
            RegisteredOriginalCandidateService.Candidate candidate,
            BlockchainOriginalResponse original) {
    }

    private record CandidateConfirmation(
            ConfirmedCandidate confirmed,
            RegistryFailure failure) {
    }
}
