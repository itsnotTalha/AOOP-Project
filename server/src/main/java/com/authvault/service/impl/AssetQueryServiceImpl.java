package com.authvault.service.impl;

import com.authvault.dto.asset.AssetDetailResponse;
import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.VerificationHistoryResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationHistoryRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetQueryService;
import com.authvault.service.AssetStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AssetQueryServiceImpl implements AssetQueryService {

    private static final int MAX_DOWNLOAD_FILENAME_LENGTH = 255;

    private final DigitalAssetRepository digitalAssetRepository;
    private final VerificationHistoryRepository verificationHistoryRepository;
    private final AssetStorageService assetStorageService;

    public AssetQueryServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            VerificationHistoryRepository verificationHistoryRepository,
            AssetStorageService assetStorageService) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.verificationHistoryRepository = verificationHistoryRepository;
        this.assetStorageService = assetStorageService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> listOwnedAssets() {
        User currentUser = SecurityUtils.getCurrentUser();
        return digitalAssetRepository.findByCurrentOwnerOrderByUploadDateDesc(currentUser)
                .stream()
                .map(this::toAssetResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetDetailResponse getOwnedAsset(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        List<VerificationHistoryResponse> history = verificationHistoryRepository.findByAsset(asset)
                .stream()
                .sorted(Comparator.comparing(VerificationHistory::getVerifiedAt).reversed())
                .map(this::toHistoryResponse)
                .toList();

        return AssetDetailResponse.builder()
                .assetId(asset.getUuid())
                .title(asset.getTitle())
                .description(asset.getDescription())
                .assetType(asset.getAssetType().name())
                .originalFilename(asset.getOriginalFilename())
                .mimeType(asset.getMimeType())
                .fileSize(asset.getFileSize())
                .sha256Hash(asset.getSha256Hash())
                .verificationStatus(asset.getVerificationStatus().name())
                .uploadDate(asset.getUploadDate())
                .verificationHistory(history)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetDownload openOwnedAssetDownload(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        return new AssetDownload(
                assetStorageService.loadStoredAsset(asset.getStoragePath()),
                safeDownloadFilename(asset.getOriginalFilename()),
                asset.getMimeType());
    }

    private DigitalAsset findOwnedAsset(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        return digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private AssetResponse toAssetResponse(DigitalAsset asset) {
        return AssetResponse.builder()
                .assetId(asset.getUuid())
                .title(asset.getTitle())
                .description(asset.getDescription())
                .assetType(asset.getAssetType().name())
                .originalFilename(asset.getOriginalFilename())
                .mimeType(asset.getMimeType())
                .fileSize(asset.getFileSize())
                .sha256Hash(asset.getSha256Hash())
                .verificationStatus(asset.getVerificationStatus().name())
                .uploadDate(asset.getUploadDate())
                .build();
    }

    private VerificationHistoryResponse toHistoryResponse(VerificationHistory history) {
        return VerificationHistoryResponse.builder()
                .verificationMethod(history.getVerificationMethod().name())
                .result(history.getResult().name())
                .notes(history.getNotes())
                .verifiedAt(history.getVerifiedAt())
                .build();
    }

    private String safeDownloadFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "download";
        }

        String safeFilename = originalFilename
                .replaceAll("[\\r\\n\\p{Cntrl}/\\\\\"]", "_")
                .replaceAll("\\.{2,}", "_")
                .trim();
        if (safeFilename.isBlank()) {
            return "download";
        }
        if (safeFilename.length() > MAX_DOWNLOAD_FILENAME_LENGTH) {
            return safeFilename.substring(0, MAX_DOWNLOAD_FILENAME_LENGTH);
        }
        return safeFilename;
    }
}
