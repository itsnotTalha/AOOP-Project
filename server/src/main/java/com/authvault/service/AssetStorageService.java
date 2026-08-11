package com.authvault.service;

import java.io.InputStream;
import java.nio.file.Path;

public interface AssetStorageService {

    Path createTempFile(String validatedExtension);

    StoredAsset commitValidatedImage(Path validatedTempFile, String validatedExtension);

    StoredAsset commitValidatedDocument(Path validatedTempFile, String validatedExtension);

    InputStream loadStoredAsset(String storageKey);

    void deleteTempQuietly(Path temporaryFile);

    void cleanupCommittedFileAfterPersistenceFailure(StoredAsset storedAsset);

    record StoredAsset(String storageKey, String storedFilename) {
    }
}
