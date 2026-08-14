package com.authvault.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

public interface AssetStorageService {

    Path createTempFile(String validatedExtension);

    StoredAsset commitValidatedImage(Path validatedTempFile, String validatedExtension);

    StoredAsset commitValidatedDocument(Path validatedTempFile, String validatedExtension);

    InputStream loadStoredAsset(String storageKey);

    void deleteTempQuietly(Path temporaryFile);

    void cleanupCommittedFileAfterPersistenceFailure(StoredAsset storedAsset);

    Optional<StagedDeletion> stageStoredAssetForDeletion(String storageKey);

    void restoreStagedDeletion(StagedDeletion stagedDeletion);

    void deleteStagedDeletionQuietly(StagedDeletion stagedDeletion);

    record StoredAsset(String storageKey, String storedFilename) {
    }

    record StagedDeletion(String originalStorageKey, String quarantineFilename) {
    }
}
