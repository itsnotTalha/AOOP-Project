package com.authvault.service.impl;

import com.authvault.blockchain.CreatorIdHasher;
import com.authvault.blockchain.OriginalRegistryClient;
import com.authvault.blockchain.OriginalRegistryClientException;
import com.authvault.blockchain.RegistrationEvidenceHasher;
import com.authvault.blockchain.RegistrationEvidenceV1;
import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.BlockchainException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationHistoryRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.BlockchainRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class BlockchainRegistryServiceImpl implements BlockchainRegistryService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private final DigitalAssetRepository digitalAssetRepository;
    private final VerificationHistoryRepository verificationHistoryRepository;
    private final OriginalRegistryClient registryClient;
    private final CreatorIdHasher creatorIdHasher;
    private final RegistrationEvidenceHasher evidenceHasher;

    public BlockchainRegistryServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            VerificationHistoryRepository verificationHistoryRepository,
            OriginalRegistryClient registryClient,
            CreatorIdHasher creatorIdHasher,
            RegistrationEvidenceHasher evidenceHasher) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.verificationHistoryRepository = verificationHistoryRepository;
        this.registryClient = registryClient;
        this.creatorIdHasher = creatorIdHasher;
        this.evidenceHasher = evidenceHasher;
    }

    @Override
    @Transactional(readOnly = true)
    public BlockchainRegistrationResponse registerOwnedAsset(String assetId) {
        ensureEnabled();
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = findOwnedAsset(assetId, currentUser);
        validateEligibility(asset);

        LocalDateTime lastVerifiedAt = latestVerificationTimestamp(asset);
        String creatorIdHash = creatorIdHasher.hash(currentUser);
        RegistrationEvidenceV1 evidence = new RegistrationEvidenceV1(
                asset.getUuid(),
                asset.getAssetType().name(),
                asset.getSha256Hash(),
                asset.getVerificationStatus().name(),
                lastVerifiedAt);
        OriginalRegistryClient.RegistrationRequest request =
                new OriginalRegistryClient.RegistrationRequest(
                        asset.getUuid(),
                        creatorIdHash,
                        asset.getSha256Hash(),
                        asset.getAssetType().name(),
                        asset.getVerificationStatus().name(),
                        evidenceHasher.hash(evidence));

        return execute(() -> registryClient.registerOriginal(request));
    }

    @Override
    @Transactional(readOnly = true)
    public BlockchainOriginalResponse getOwnedAssetRegistration(String assetId) {
        ensureEnabled();
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = findOwnedAsset(assetId, currentUser);
        return execute(() -> registryClient.getOriginal(asset.getUuid()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockchainHistoryResponse> getOwnedAssetHistory(String assetId) {
        ensureEnabled();
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = findOwnedAsset(assetId, currentUser);
        return execute(() -> registryClient.getAssetHistory(asset.getUuid()));
    }

    @Override
    public BlockchainOriginalLookupResponse findBySha256(String sha256) {
        validateSha256(sha256);
        ensureEnabled();
        Optional<BlockchainOriginalResponse> original =
                execute(() -> registryClient.findBySha256(sha256));
        return new BlockchainOriginalLookupResponse(original.isPresent(), original.orElse(null));
    }

    private DigitalAsset findOwnedAsset(String assetId, User currentUser) {
        return digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private void validateEligibility(DigitalAsset asset) {
        if (asset.getVerificationStatus() != DigitalAsset.VerificationStatus.VERIFIED) {
            throw new BadRequestException("Only VERIFIED assets can be registered on the blockchain");
        }
        if (asset.getAssetType() != DigitalAsset.AssetType.IMAGE
                && asset.getAssetType() != DigitalAsset.AssetType.DOCUMENT) {
            throw new BadRequestException("Asset type is not supported by the blockchain registry");
        }
        validateSha256(asset.getSha256Hash());
    }

    private LocalDateTime latestVerificationTimestamp(DigitalAsset asset) {
        List<VerificationHistory> history =
                verificationHistoryRepository.findByAssetOrderByVerifiedAtDesc(asset);
        if (history.isEmpty() || history.getFirst().getVerifiedAt() == null) {
            throw new BadRequestException("Verified asset has no verification timestamp");
        }
        return history.getFirst().getVerifiedAt();
    }

    private void validateSha256(String sha256) {
        if (sha256 == null || !SHA256_PATTERN.matcher(sha256).matches()) {
            throw new BadRequestException(
                    "SHA-256 must be exactly 64 lowercase hexadecimal characters");
        }
    }

    private void ensureEnabled() {
        if (!registryClient.isEnabled()) {
            throw mapClientException(new OriginalRegistryClientException(
                    OriginalRegistryClientException.Reason.DISABLED,
                    "Blockchain integration is disabled"));
        }
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OriginalRegistryClientException exception) {
            throw mapClientException(exception);
        }
    }

    private BlockchainException mapClientException(OriginalRegistryClientException exception) {
        return switch (exception.getReason()) {
            case DISABLED -> new BlockchainException(
                    "BLOCKCHAIN_DISABLED",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Blockchain integration is disabled",
                    exception);
            case UNAVAILABLE -> new BlockchainException(
                    "BLOCKCHAIN_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The blockchain registry is unavailable",
                    exception);
            case REGISTRATION_FAILED -> new BlockchainException(
                    "BLOCKCHAIN_REGISTRATION_FAILED",
                    HttpStatus.BAD_GATEWAY,
                    "Blockchain registration failed",
                    exception);
            case ASSET_NOT_FOUND -> new BlockchainException(
                    "BLOCKCHAIN_ASSET_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "Blockchain original not found",
                    exception);
            case DUPLICATE_ASSET -> new BlockchainException(
                    "BLOCKCHAIN_DUPLICATE_ASSET",
                    HttpStatus.CONFLICT,
                    "The asset is already registered on the blockchain",
                    exception);
            case DUPLICATE_SHA256 -> new BlockchainException(
                    "BLOCKCHAIN_DUPLICATE_SHA256",
                    HttpStatus.CONFLICT,
                    "The SHA-256 is already registered on the blockchain",
                    exception);
            case INVALID_RESPONSE -> new BlockchainException(
                    "BLOCKCHAIN_INVALID_RESPONSE",
                    HttpStatus.BAD_GATEWAY,
                    "The blockchain registry returned an invalid response",
                    exception);
        };
    }
}
