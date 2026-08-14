package com.authvault.service.impl;

import com.authvault.config.UploadProperties;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.AssetStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetManagementServiceImplTest {

    @TempDir
    Path testDirectory;

    private User currentUser;
    private LocalAssetStorageService storageService;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setRootDirectory(testDirectory.resolve("uploads"));
        storageService = new LocalAssetStorageService(uploadProperties);

        currentUser = authenticatedUser();
        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresOriginalFileWhenDatabaseDeletionFailsAfterStaging() throws Exception {
        byte[] content = "preserve-after-db-failure".getBytes(StandardCharsets.UTF_8);
        Path temporaryFile = storageService.createTempFile("png");
        Files.write(temporaryFile, content);
        AssetStorageService.StoredAsset storedAsset =
                storageService.commitValidatedImage(temporaryFile, "png");

        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setCurrentOwner(currentUser);
        asset.setStoragePath(storedAsset.storageKey());

        DigitalAssetRepository repository = repositoryForDelete(asset, true);
        AssetManagementServiceImpl service =
                new AssetManagementServiceImpl(repository, storageService);

        assertThrows(IllegalStateException.class,
                () -> service.deleteOwnedAsset(asset.getUuid()));

        try (var inputStream = storageService.loadStoredAsset(storedAsset.storageKey())) {
            assertArrayEquals(content, inputStream.readAllBytes());
        }
    }

    @Test
    void permanentlyDeletesQuarantinedFileAfterDatabaseCommit() throws Exception {
        Path temporaryFile = storageService.createTempFile("png");
        Files.writeString(temporaryFile, "delete-after-commit");
        AssetStorageService.StoredAsset storedAsset =
                storageService.commitValidatedImage(temporaryFile, "png");

        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setCurrentOwner(currentUser);
        asset.setStoragePath(storedAsset.storageKey());
        AssetManagementServiceImpl service = new AssetManagementServiceImpl(
                repositoryForDelete(asset, false), storageService);

        TransactionSynchronizationManager.initSynchronization();
        service.deleteOwnedAsset(asset.getUuid());

        assertFalse(Files.exists(testDirectory.resolve("uploads")
                .resolve(storedAsset.storageKey())));
        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED));

        try (var temporaryFiles = Files.list(testDirectory.resolve("uploads/temp"))) {
            assertTrue(temporaryFiles.findAny().isEmpty());
        }
    }

    private DigitalAssetRepository repositoryForDelete(
            DigitalAsset asset,
            boolean failDelete) {
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
            case "findByUuidAndCurrentOwner" -> Optional.of(asset);
            case "delete" -> {
                if (failDelete) {
                    throw new IllegalStateException("simulated database delete failure");
                }
                yield null;
            }
            case "flush" -> null;
            case "toString" -> "DigitalAssetRepositoryStub";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw unsupported(method);
        };
        return (DigitalAssetRepository) Proxy.newProxyInstance(
                DigitalAssetRepository.class.getClassLoader(),
                new Class<?>[]{DigitalAssetRepository.class},
                handler);
    }

    private UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException(method.getName());
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(11L);
        user.setUuid(UUID.randomUUID().toString());
        user.setFullName("Asset Owner");
        user.setUsername("asset-owner");
        user.setEmail("asset-owner@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
