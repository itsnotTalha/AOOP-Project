package com.authvault.service.impl;

import com.authvault.config.UploadProperties;
import com.authvault.exception.FileStorageException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.service.AssetStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalAssetStorageService implements AssetStorageService {

    private static final String TEMP_DIRECTORY = "temp";
    private static final String IMAGE_DIRECTORY = "images";
    private static final String DOCUMENT_DIRECTORY = "documents";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf");
    private static final Set<String> ALL_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    private static final int NAME_GENERATION_ATTEMPTS = 10;

    private final Path uploadRoot;
    private final Path tempRoot;
    private final Path imageRoot;
    private final Path documentRoot;

    public LocalAssetStorageService(UploadProperties uploadProperties) {
        try {
            Path configuredRoot = uploadProperties.getRootDirectory().toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            Path initializedRoot = configuredRoot.toRealPath();
            applyDirectoryPermissions(initializedRoot);

            Path initializedTempRoot = initializeManagedDirectory(initializedRoot, TEMP_DIRECTORY);
            Path initializedImageRoot = initializeManagedDirectory(initializedRoot, IMAGE_DIRECTORY);
            Path initializedDocumentRoot = initializeManagedDirectory(initializedRoot, DOCUMENT_DIRECTORY);

            this.uploadRoot = initializedRoot;
            this.tempRoot = initializedTempRoot;
            this.imageRoot = initializedImageRoot;
            this.documentRoot = initializedDocumentRoot;
        } catch (IOException | RuntimeException exception) {
            throw new FileStorageException("Could not initialize upload storage", exception);
        }
    }

    @Override
    public Path createTempFile(String validatedExtension) {
        String extension = requireAllowedExtension(validatedExtension, ALL_EXTENSIONS);
        requireManagedDirectory(tempRoot);

        for (int attempt = 0; attempt < NAME_GENERATION_ATTEMPTS; attempt++) {
            String generatedName = UUID.randomUUID() + "." + extension + ".tmp";
            Path temporaryFile = resolveInside(tempRoot, generatedName);
            boolean created = false;
            try {
                Files.createFile(temporaryFile);
                created = true;
                applyFilePermissions(temporaryFile);
                return temporaryFile;
            } catch (FileAlreadyExistsException ignored) {
                // Generate another UUID without overwriting an existing temporary file.
            } catch (IOException exception) {
                if (created) {
                    deleteAfterFailedCreation(temporaryFile, exception);
                }
                throw new FileStorageException("Could not create temporary upload file", exception);
            }
        }

        throw new FileStorageException("Could not allocate a unique temporary upload file");
    }

    @Override
    public StoredAsset commitValidatedImage(Path validatedTempFile, String validatedExtension) {
        String extension = requireAllowedExtension(validatedExtension, IMAGE_EXTENSIONS);
        return commit(validatedTempFile, extension, imageRoot, IMAGE_DIRECTORY, "image");
    }

    @Override
    public StoredAsset commitValidatedDocument(Path validatedTempFile, String validatedExtension) {
        String extension = requireAllowedExtension(validatedExtension, DOCUMENT_EXTENSIONS);
        return commit(validatedTempFile, extension, documentRoot, DOCUMENT_DIRECTORY, "document");
    }

    @Override
    public InputStream loadStoredAsset(String storageKey) {
        Path storedFile = resolveCommittedStorageKey(storageKey);
        try {
            if (Files.isSymbolicLink(storedFile)
                    || !Files.isRegularFile(storedFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new ResourceNotFoundException("Stored asset file was not found");
            }

            Path realStoredFile = storedFile.toRealPath();
            if (!realStoredFile.startsWith(uploadRoot)) {
                throw new FileStorageException("Invalid stored asset location");
            }
            return Files.newInputStream(realStoredFile);
        } catch (ResourceNotFoundException | FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Could not load stored asset file", exception);
        }
    }

    @Override
    public void deleteTempQuietly(Path temporaryFile) {
        if (!isManagedDirectoryAvailable(tempRoot) || !isDirectChild(temporaryFile, tempRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException ignored) {
            // Best-effort cleanup must not replace the original upload failure.
        }
    }

    @Override
    public void cleanupCommittedFileAfterPersistenceFailure(StoredAsset storedAsset) {
        if (storedAsset == null) {
            return;
        }
        try {
            Path storedFile = resolveCommittedStorageKey(storedAsset.storageKey());
            Files.deleteIfExists(storedFile);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort compensation must not replace the persistence failure.
        }
    }

    private StoredAsset commit(
            Path validatedTempFile,
            String extension,
            Path destinationRoot,
            String directoryName,
            String assetLabel) {
        requireManagedDirectory(tempRoot);
        requireManagedDirectory(destinationRoot);
        Path managedTempFile = requireManagedTempFile(validatedTempFile);

        for (int attempt = 0; attempt < NAME_GENERATION_ATTEMPTS; attempt++) {
            String storedFilename = UUID.randomUUID() + "." + extension;
            Path destination = resolveInside(destinationRoot, storedFilename);
            boolean moved = false;
            try {
                moveWithAtomicFallback(managedTempFile, destination);
                moved = true;
                applyFilePermissions(destination);
                return new StoredAsset(directoryName + "/" + storedFilename, storedFilename);
            } catch (FileAlreadyExistsException ignored) {
                // Generate another UUID without overwriting an existing asset.
            } catch (IOException exception) {
                if (moved) {
                    deleteAfterFailedCreation(destination, exception);
                }
                throw new FileStorageException("Could not commit validated " + assetLabel + " file", exception);
            }
        }

        throw new FileStorageException("Could not allocate a unique stored asset filename");
    }

    private void moveWithAtomicFallback(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void deleteAfterFailedCreation(Path file, IOException originalFailure) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private Path requireManagedTempFile(Path temporaryFile) {
        if (!isDirectChild(temporaryFile, tempRoot)) {
            throw new FileStorageException("Invalid temporary upload file");
        }

        Path normalized = temporaryFile.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    || !normalized.toRealPath().getParent().equals(tempRoot)) {
                throw new FileStorageException("Invalid temporary upload file");
            }
            return normalized;
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Temporary upload file is unavailable", exception);
        }
    }

    private Path resolveCommittedStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new FileStorageException("Invalid stored asset location");
        }

        String portableKey = storageKey.replace('\\', '/');
        Path relativePath;
        try {
            relativePath = Path.of(portableKey).normalize();
        } catch (RuntimeException exception) {
            throw new FileStorageException("Invalid stored asset location", exception);
        }

        if (relativePath.isAbsolute() || relativePath.getNameCount() != 2) {
            throw new FileStorageException("Invalid stored asset location");
        }

        String directoryName = relativePath.getName(0).toString();
        Path expectedRoot = switch (directoryName) {
            case IMAGE_DIRECTORY -> imageRoot;
            case DOCUMENT_DIRECTORY -> documentRoot;
            default -> throw new FileStorageException("Invalid stored asset location");
        };
        requireManagedDirectory(expectedRoot);

        Path resolved = uploadRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(expectedRoot) || !resolved.getParent().equals(expectedRoot)) {
            throw new FileStorageException("Invalid stored asset location");
        }
        return resolved;
    }

    private String requireAllowedExtension(String extension, Set<String> allowedExtensions) {
        if (extension == null) {
            throw new FileStorageException("Invalid validated file extension");
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (!allowedExtensions.contains(normalized)) {
            throw new FileStorageException("Invalid validated file extension");
        }
        return normalized;
    }

    private Path initializeManagedDirectory(Path root, String directoryName) throws IOException {
        Path directory = resolveInside(root, directoryName);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new FileStorageException("Upload storage contains an unsafe directory link");
        }

        Path realDirectory = directory.toRealPath();
        if (!realDirectory.startsWith(root) || !realDirectory.getParent().equals(root)) {
            throw new FileStorageException("Upload directory is outside the configured root");
        }
        applyDirectoryPermissions(realDirectory);
        return realDirectory;
    }

    private Path resolveInside(Path root, String generatedName) {
        Path resolved = root.resolve(generatedName).normalize();
        if (!resolved.startsWith(root) || !resolved.getParent().equals(root)) {
            throw new FileStorageException("Invalid storage destination");
        }
        return resolved;
    }

    private boolean isDirectChild(Path candidate, Path expectedParent) {
        if (candidate == null) {
            return false;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        return normalized.startsWith(expectedParent) && expectedParent.equals(normalized.getParent());
    }

    private void requireManagedDirectory(Path directory) {
        try {
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.toRealPath().equals(directory)
                    || !directory.startsWith(uploadRoot)
                    || !uploadRoot.equals(directory.getParent())) {
                throw new FileStorageException("Managed upload directory is unavailable");
            }
        } catch (FileStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Managed upload directory is unavailable", exception);
        }
    }

    private boolean isManagedDirectoryAvailable(Path directory) {
        try {
            requireManagedDirectory(directory);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void applyDirectoryPermissions(Path directory) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private void applyFilePermissions(Path file) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
