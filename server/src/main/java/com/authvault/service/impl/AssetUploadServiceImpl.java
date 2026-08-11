package com.authvault.service.impl;

import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.AssetUploadRequest;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.Document;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.DuplicateFileException;
import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.exception.FileStorageException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetStorageService;
import com.authvault.service.AssetUploadService;
import com.authvault.service.Sha256Service;
import com.authvault.validation.PreparedUploadFile;
import com.authvault.validation.UploadFileValidator;
import com.authvault.validation.ValidatedUploadFile;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AssetUploadServiceImpl implements AssetUploadService {

    private static final int MAX_TITLE_LENGTH = 150;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final DigitalAssetRepository digitalAssetRepository;
    private final UploadFileValidator uploadFileValidator;
    private final AssetStorageService assetStorageService;
    private final Sha256Service sha256Service;

    public AssetUploadServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            UploadFileValidator uploadFileValidator,
            AssetStorageService assetStorageService,
            Sha256Service sha256Service) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.uploadFileValidator = uploadFileValidator;
        this.assetStorageService = assetStorageService;
        this.sha256Service = sha256Service;
    }

    @Override
    @Transactional
    public AssetResponse uploadImage(AssetUploadRequest request) {
        return upload(request, DigitalAsset.AssetType.IMAGE);
    }

    @Override
    @Transactional
    public AssetResponse uploadDocument(AssetUploadRequest request) {
        return upload(request, DigitalAsset.AssetType.DOCUMENT);
    }

    private AssetResponse upload(AssetUploadRequest request, DigitalAsset.AssetType assetType) {
        User currentUser = SecurityUtils.getCurrentUser();
        NormalizedMetadata metadata = validateMetadata(request);
        MultipartFile multipartFile = request.getFile();
        PreparedUploadFile preparedFile = uploadFileValidator.prepare(multipartFile, assetType);

        Path temporaryFile = null;
        AssetStorageService.StoredAsset storedAsset = null;
        try {
            temporaryFile = assetStorageService.createTempFile(preparedFile.extension());
            StreamedUpload streamedUpload = streamToTempAndHash(
                    multipartFile,
                    temporaryFile,
                    preparedFile.maxFileSizeBytes(),
                    assetType);

            ValidatedUploadFile validatedFile = uploadFileValidator.validateTempFile(
                    temporaryFile,
                    preparedFile,
                    streamedUpload.fileSize());

            if (digitalAssetRepository.existsBySha256Hash(streamedUpload.sha256Hash())) {
                assetStorageService.deleteTempQuietly(temporaryFile);
                temporaryFile = null;
                throw new DuplicateFileException();
            }

            storedAsset = commit(temporaryFile, validatedFile, assetType);
            registerRollbackCleanup(storedAsset);
            DigitalAsset asset = createAsset(
                    currentUser,
                    metadata,
                    validatedFile,
                    streamedUpload.sha256Hash(),
                    storedAsset,
                    assetType);

            DigitalAsset persistedAsset = digitalAssetRepository.saveAndFlush(asset);
            return toResponse(persistedAsset);
        } catch (RuntimeException exception) {
            if (storedAsset != null) {
                assetStorageService.cleanupCommittedFileAfterPersistenceFailure(storedAsset);
            }
            if (isSha256UniqueConstraintViolation(exception)) {
                throw new DuplicateFileException(exception);
            }
            throw exception;
        } finally {
            assetStorageService.deleteTempQuietly(temporaryFile);
        }
    }

    private boolean isSha256UniqueConstraintViolation(RuntimeException exception) {
        if (!(exception instanceof DataIntegrityViolationException)) {
            return false;
        }

        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("sha256_hash")) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private NormalizedMetadata validateMetadata(AssetUploadRequest request) {
        if (request == null) {
            throw new BadRequestException("Upload request is required");
        }

        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("Title must not exceed 150 characters");
        }

        String description = request.getDescription();
        if (description != null) {
            description = description.trim();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                throw new BadRequestException("Description must not exceed 2000 characters");
            }
            if (description.isBlank()) {
                description = null;
            }
        }

        return new NormalizedMetadata(title, description);
    }

    private StreamedUpload streamToTempAndHash(
            MultipartFile multipartFile,
            Path temporaryFile,
            long maxFileSizeBytes,
            DigitalAsset.AssetType assetType) {
        try (InputStream inputStream = multipartFile.getInputStream();
             OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
            Sha256Service.Sha256Result result = sha256Service.copyAndCalculate(
                    inputStream, outputStream, maxFileSizeBytes);
            if (result.bytesRead() == 0) {
                throw new BadRequestException("Uploaded file is required and must not be empty");
            }
            return new StreamedUpload(result.bytesRead(), result.hash());
        } catch (FileSizeLimitExceededException exception) {
            throw new FileSizeLimitExceededException(
                    assetType + " file exceeds the configured maximum size of "
                            + maxFileSizeBytes + " bytes");
        } catch (IOException exception) {
            throw new FileStorageException("Could not process uploaded file", exception);
        }
    }

    private AssetStorageService.StoredAsset commit(
            Path temporaryFile,
            ValidatedUploadFile validatedFile,
            DigitalAsset.AssetType assetType) {
        return switch (assetType) {
            case IMAGE -> assetStorageService.commitValidatedImage(
                    temporaryFile, validatedFile.extension());
            case DOCUMENT -> assetStorageService.commitValidatedDocument(
                    temporaryFile, validatedFile.extension());
        };
    }

    private void registerRollbackCleanup(AssetStorageService.StoredAsset storedAsset) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    assetStorageService.cleanupCommittedFileAfterPersistenceFailure(storedAsset);
                }
            }
        });
    }

    private DigitalAsset createAsset(
            User currentUser,
            NormalizedMetadata metadata,
            ValidatedUploadFile validatedFile,
            String sha256Hash,
            AssetStorageService.StoredAsset storedAsset,
            DigitalAsset.AssetType assetType) {
        LocalDateTime now = LocalDateTime.now();
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setOwner(currentUser);
        asset.setCurrentOwner(currentUser);
        asset.setTitle(metadata.title());
        asset.setDescription(metadata.description());
        asset.setAssetType(assetType);
        asset.setOriginalFilename(validatedFile.displayFilename());
        asset.setStoredFilename(storedAsset.storedFilename());
        asset.setStoragePath(storedAsset.storageKey());
        asset.setMimeType(validatedFile.mimeType());
        asset.setFileSize(validatedFile.fileSize());
        asset.setSha256Hash(sha256Hash);
        asset.setUploadDate(now);
        asset.setVerificationStatus(DigitalAsset.VerificationStatus.VERIFIED);

        if (assetType == DigitalAsset.AssetType.DOCUMENT) {
            Document document = new Document();
            document.setAsset(asset);
            document.setCreatedAt(now);
            asset.setDocument(document);
        }

        VerificationHistory verificationHistory = new VerificationHistory();
        verificationHistory.setAsset(asset);
        verificationHistory.setVerifiedBy(currentUser);
        verificationHistory.setVerificationMethod(VerificationHistory.VerificationMethod.SHA256_UPLOAD);
        verificationHistory.setResult(VerificationHistory.Result.VERIFIED);
        verificationHistory.setVerifiedAt(now);
        asset.getVerificationHistory().add(verificationHistory);

        return asset;
    }

    private AssetResponse toResponse(DigitalAsset asset) {
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

    private record NormalizedMetadata(String title, String description) {
    }

    private record StreamedUpload(long fileSize, String sha256Hash) {
    }
}
