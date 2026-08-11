package com.authvault.service.impl;

import com.authvault.dto.asset.AssetVerificationResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.FileStorageException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetIntegrityService;
import com.authvault.service.AssetStorageService;
import com.authvault.service.Sha256Service;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class AssetIntegrityServiceImpl implements AssetIntegrityService {

    private static final String MATCH_NOTE = "Stored file hash matches the upload hash";
    private static final String MISMATCH_NOTE = "Stored file hash does not match the upload hash";
    private static final String UNREADABLE_NOTE = "Stored file could not be read";

    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetStorageService assetStorageService;
    private final Sha256Service sha256Service;

    public AssetIntegrityServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            AssetStorageService assetStorageService,
            Sha256Service sha256Service) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.assetStorageService = assetStorageService;
        this.sha256Service = sha256Service;
    }

    @Override
    @Transactional
    public AssetVerificationResponse verifyIntegrity(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        String currentHash = null;
        boolean hashMatches = false;
        String notes;
        try (InputStream inputStream = assetStorageService.loadStoredAsset(asset.getStoragePath())) {
            currentHash = sha256Service.calculate(inputStream).hash();
            hashMatches = asset.getSha256Hash().equals(currentHash);
            notes = hashMatches ? MATCH_NOTE : MISMATCH_NOTE;
        } catch (ResourceNotFoundException | FileStorageException | IOException exception) {
            currentHash = null;
            hashMatches = false;
            notes = UNREADABLE_NOTE;
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        DigitalAsset.VerificationStatus status = hashMatches
                ? DigitalAsset.VerificationStatus.VERIFIED
                : DigitalAsset.VerificationStatus.REJECTED;
        VerificationHistory.Result historyResult = hashMatches
                ? VerificationHistory.Result.VERIFIED
                : VerificationHistory.Result.REJECTED;

        asset.setVerificationStatus(status);
        VerificationHistory history = new VerificationHistory();
        history.setAsset(asset);
        history.setVerifiedBy(currentUser);
        history.setVerificationMethod(VerificationHistory.VerificationMethod.SHA256_INTEGRITY);
        history.setResult(historyResult);
        history.setNotes(notes);
        history.setVerifiedAt(verifiedAt);
        asset.getVerificationHistory().add(history);
        digitalAssetRepository.saveAndFlush(asset);

        return AssetVerificationResponse.builder()
                .assetId(asset.getUuid())
                .originalHash(asset.getSha256Hash())
                .currentHash(currentHash)
                .hashMatches(hashMatches)
                .verificationStatus(status.name())
                .verifiedAt(verifiedAt)
                .build();
    }
}
