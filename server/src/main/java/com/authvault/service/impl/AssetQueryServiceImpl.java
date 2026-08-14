package com.authvault.service.impl;

import com.authvault.dto.asset.AssetDetailResponse;
import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.VerificationHistoryResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationHistoryRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetQueryService;
import com.authvault.service.AssetStorageService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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
    public List<AssetResponse> listOwnedAssets(
            String type,
            String status,
            String search,
            String sort) {
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset.AssetType assetType = parseAssetType(type);
        DigitalAsset.VerificationStatus verificationStatus = parseVerificationStatus(status);
        String normalizedSearch = normalizeSearch(search);
        Sort requestedSort = parseSort(sort);

        Specification<DigitalAsset> specification = (root, query, builder) ->
                builder.equal(root.get("currentOwner"), currentUser);
        if (assetType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("assetType"), assetType));
        }
        if (verificationStatus != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("verificationStatus"), verificationStatus));
        }
        if (normalizedSearch != null) {
            String pattern = "%" + escapeLikePattern(normalizedSearch.toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("originalFilename")), pattern, '\\')));
        }

        return digitalAssetRepository.findAll(specification, requestedSort)
                .stream()
                .map(this::toAssetResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetDetailResponse getOwnedAsset(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        List<VerificationHistory> historyRecords =
                verificationHistoryRepository.findByAssetOrderByVerifiedAtDesc(asset);
        List<VerificationHistoryResponse> history = historyRecords
                .stream()
                .map(this::toHistoryResponse)
                .toList();
        var lastVerifiedAt = historyRecords.isEmpty()
                ? null
                : historyRecords.getFirst().getVerifiedAt();

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
                .lastVerifiedAt(lastVerifiedAt)
                .verificationHistory(history)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationHistoryResponse> getOwnedAssetVerificationHistory(String assetId) {
        DigitalAsset asset = findOwnedAsset(assetId);
        return verificationHistoryRepository.findByAssetOrderByVerifiedAtDesc(asset)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
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

    private DigitalAsset.AssetType parseAssetType(String value) {
        return parseEnum(value, DigitalAsset.AssetType.class, "type");
    }

    private DigitalAsset.VerificationStatus parseVerificationStatus(String value) {
        return parseEnum(value, DigitalAsset.VerificationStatus.class, "status");
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String parameterName) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid " + parameterName + " query parameter");
        }
    }

    private Sort parseSort(String value) {
        String requestedSort = value == null ? "newest" : value;
        Sort.Direction direction = switch (requestedSort) {
            case "newest" -> Sort.Direction.DESC;
            case "oldest" -> Sort.Direction.ASC;
            default -> throw new BadRequestException("Invalid sort query parameter");
        };
        return Sort.by(direction, "uploadDate").and(Sort.by(direction, "uuid"));
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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
