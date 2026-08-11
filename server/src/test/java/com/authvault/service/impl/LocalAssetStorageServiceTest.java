package com.authvault.service.impl;

import com.authvault.config.UploadProperties;
import com.authvault.exception.FileStorageException;
import com.authvault.service.AssetStorageService.StoredAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAssetStorageServiceTest {

    @TempDir
    Path testDirectory;

    private Path uploadRoot;
    private LocalAssetStorageService storageService;

    @BeforeEach
    void setUp() {
        uploadRoot = testDirectory.resolve("uploads");
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setRootDirectory(uploadRoot);
        storageService = new LocalAssetStorageService(uploadProperties);
    }

    @Test
    void createsManagedDirectoriesAndUuidNamedTemporaryFile() {
        Path temporaryFile = storageService.createTempFile("png");

        assertTrue(Files.isDirectory(uploadRoot.resolve("temp")));
        assertTrue(Files.isDirectory(uploadRoot.resolve("images")));
        assertTrue(Files.isDirectory(uploadRoot.resolve("documents")));
        assertTrue(temporaryFile.getFileName().toString()
                .matches("[0-9a-f-]{36}\\.png\\.tmp"));
        assertTrue(temporaryFile.normalize().startsWith(uploadRoot.resolve("temp").normalize()));
    }

    @Test
    void commitsAndLoadsImageUsingRelativeStorageKey() throws Exception {
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
        Path temporaryFile = storageService.createTempFile("jpg");
        Files.write(temporaryFile, content);

        StoredAsset storedAsset = storageService.commitValidatedImage(temporaryFile, "jpg");

        assertFalse(Files.exists(temporaryFile));
        assertTrue(storedAsset.storedFilename().matches("[0-9a-f-]{36}\\.jpg"));
        assertTrue(storedAsset.storageKey().matches("images/[0-9a-f-]{36}\\.jpg"));
        assertFalse(Path.of(storedAsset.storageKey()).isAbsolute());
        try (InputStream inputStream = storageService.loadStoredAsset(storedAsset.storageKey())) {
            assertArrayEquals(content, inputStream.readAllBytes());
        }
    }

    @Test
    void commitsDocumentOnlyToDocumentDirectory() throws Exception {
        Path temporaryFile = storageService.createTempFile("pdf");
        Files.writeString(temporaryFile, "%PDF-test");

        StoredAsset storedAsset = storageService.commitValidatedDocument(temporaryFile, "pdf");

        assertTrue(storedAsset.storageKey().matches("documents/[0-9a-f-]{36}\\.pdf"));
        assertTrue(Files.exists(uploadRoot.resolve(storedAsset.storageKey())));
        assertThrows(FileStorageException.class,
                () -> storageService.commitValidatedImage(
                        storageService.createTempFile("pdf"), "pdf"));
    }

    @Test
    void rejectsTraversalAndAbsoluteStorageKeysWithoutLeakingRootPath() {
        FileStorageException traversal = assertThrows(FileStorageException.class,
                () -> storageService.loadStoredAsset("images/../../outside.txt"));
        FileStorageException absolute = assertThrows(FileStorageException.class,
                () -> storageService.loadStoredAsset(testDirectory.resolve("outside.txt").toString()));

        assertFalse(traversal.getMessage().contains(testDirectory.toString()));
        assertFalse(absolute.getMessage().contains(testDirectory.toString()));
    }

    @Test
    void rejectsCommitFromOutsideManagedTempDirectory() throws Exception {
        Path externalFile = testDirectory.resolve("client-name.jpg");
        Files.writeString(externalFile, "content");

        FileStorageException exception = assertThrows(FileStorageException.class,
                () -> storageService.commitValidatedImage(externalFile, "jpg"));

        assertTrue(Files.exists(externalFile));
        assertFalse(exception.getMessage().contains(testDirectory.toString()));
    }

    @Test
    void quietlyDeletesOnlyManagedTemporaryFiles() throws Exception {
        Path managedTempFile = storageService.createTempFile("png");
        Path externalFile = testDirectory.resolve("outside.tmp");
        Files.writeString(externalFile, "keep");

        storageService.deleteTempQuietly(managedTempFile);
        storageService.deleteTempQuietly(externalFile);
        storageService.deleteTempQuietly(null);

        assertFalse(Files.exists(managedTempFile));
        assertTrue(Files.exists(externalFile));
    }

    @Test
    void cleansCommittedFileAfterPersistenceFailure() throws Exception {
        Path temporaryFile = storageService.createTempFile("jpeg");
        Files.writeString(temporaryFile, "content");
        StoredAsset storedAsset = storageService.commitValidatedImage(temporaryFile, "jpeg");
        Path committedFile = uploadRoot.resolve(storedAsset.storageKey());

        storageService.cleanupCommittedFileAfterPersistenceFailure(storedAsset);
        storageService.cleanupCommittedFileAfterPersistenceFailure(null);

        assertFalse(Files.exists(committedFile));
    }

    @Test
    void refusesDestinationDirectoryReplacedBySymlink() throws Exception {
        Path temporaryFile = storageService.createTempFile("png");
        Files.writeString(temporaryFile, "content");
        Path imageDirectory = uploadRoot.resolve("images");
        Path externalDirectory = testDirectory.resolve("external-images");
        Files.createDirectory(externalDirectory);
        Files.delete(imageDirectory);

        try {
            Files.createSymbolicLink(imageDirectory, externalDirectory);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment");
        }

        assertThrows(FileStorageException.class,
                () -> storageService.commitValidatedImage(temporaryFile, "png"));
        try (var files = Files.list(externalDirectory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }
}
