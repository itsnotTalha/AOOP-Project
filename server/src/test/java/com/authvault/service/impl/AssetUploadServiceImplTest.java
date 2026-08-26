package com.authvault.service.impl;

import com.authvault.config.UploadProperties;
import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.AssetUploadRequest;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.DuplicateFileException;
import com.authvault.exception.UnauthorizedException;
import com.authvault.exception.VerificationException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.PerceptualHashService;
import com.authvault.validation.UploadFileValidator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetUploadServiceImplTest {

    @TempDir
    Path testDirectory;

    private Path uploadRoot;
    private UploadProperties uploadProperties;
    private RepositoryStub repositoryStub;
    private AssetUploadServiceImpl uploadService;
    private User currentUser;

    @BeforeEach
    void setUp() {
        uploadRoot = testDirectory.resolve("uploads");
        uploadProperties = new UploadProperties();
        uploadProperties.setRootDirectory(uploadRoot);

        repositoryStub = new RepositoryStub();
        DigitalAssetRepository repository = repositoryStub.createProxy();
        uploadService = new AssetUploadServiceImpl(
                repository,
                new UploadFileValidator(uploadProperties),
                new LocalAssetStorageService(uploadProperties),
                new Sha256ServiceImpl(),
                new PerceptualHashServiceImpl());

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
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadsImageThroughSharedWorkflowAndReturnsDto() throws Exception {
        byte[] imageBytes = createImage("jpg");
        AssetUploadRequest request = request(
                "  Portrait  ",
                "  A profile image  ",
                new MockMultipartFile(
                        "file",
                        "../../client\\portrait.JPEG",
                        "application/pdf",
                        imageBytes));

        AssetResponse response = uploadService.uploadImage(request);

        DigitalAsset asset = repositoryStub.savedAsset;
        String expectedHash = sha256(imageBytes);
        assertNotNull(asset);
        assertTrue(response.getAssetId().matches("[0-9a-f-]{36}"));
        assertEquals(asset.getUuid(), response.getAssetId());
        assertEquals("Portrait", response.getTitle());
        assertEquals("A profile image", response.getDescription());
        assertEquals("IMAGE", response.getAssetType());
        assertEquals("portrait.jpeg", response.getOriginalFilename());
        assertEquals("image/jpeg", response.getMimeType());
        assertEquals(imageBytes.length, response.getFileSize());
        assertEquals(expectedHash, response.getSha256Hash());
        assertEquals("PENDING", response.getVerificationStatus());
        assertNotNull(response.getUploadDate());

        assertSame(currentUser, asset.getOwner());
        assertSame(currentUser, asset.getCurrentOwner());
        assertEquals("A profile image", asset.getDescription());
        assertTrue(asset.getStoredFilename().matches("[0-9a-f-]{36}\\.jpg"));
        assertTrue(asset.getStoragePath().matches("images/[0-9a-f-]{36}\\.jpg"));
        assertTrue(asset.getPerceptualHash().matches("[0-9a-f]{16}"));
        assertFalse(Path.of(asset.getStoragePath()).isAbsolute());
        assertTrue(Files.exists(uploadRoot.resolve(asset.getStoragePath())));
        assertNull(asset.getDocument());
        assertUploadVerification(asset);
        assertDirectoryEmpty(uploadRoot.resolve("temp"));
    }

    @Test
    void uploadsDocumentAndCreatesDocumentRelationWithoutSemanticProcessing() throws Exception {
        byte[] pdfBytes = createPdf();
        AssetUploadRequest request = request(
                "Report",
                null,
                new MockMultipartFile("file", "report.pdf", "image/png", pdfBytes));

        AssetResponse response = uploadService.uploadDocument(request);

        DigitalAsset asset = repositoryStub.savedAsset;
        assertEquals("DOCUMENT", response.getAssetType());
        assertEquals("application/pdf", response.getMimeType());
        assertTrue(asset.getStoragePath().matches("documents/[0-9a-f-]{36}\\.pdf"));
        assertNotNull(asset.getDocument());
        assertNull(asset.getPerceptualHash());
        assertSame(asset, asset.getDocument().getAsset());
        assertNull(asset.getDocument().getExtractedText());
        assertNull(asset.getDocument().getSemanticHash());
        assertNull(asset.getDocument().getPageCount());
        assertNull(asset.getDocument().getLanguage());
        assertNotNull(asset.getDocument().getCreatedAt());
        assertUploadVerification(asset);
        assertDirectoryEmpty(uploadRoot.resolve("temp"));
    }

    @Test
    void uploadsPngWithPersistedPerceptualHash() throws Exception {
        AssetUploadRequest request = request(
                "PNG",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        uploadService.uploadImage(request);

        assertNotNull(repositoryStub.savedAsset);
        assertTrue(repositoryStub.savedAsset.getPerceptualHash().matches("[0-9a-f]{16}"));
        assertTrue(Files.exists(uploadRoot.resolve(repositoryStub.savedAsset.getStoragePath())));
    }

    @Test
    void rejectsDuplicateHashAndCleansTemporaryFileBeforePersistence() throws Exception {
        repositoryStub.duplicate = true;
        AssetUploadRequest request = request(
                "Duplicate",
                null,
                new MockMultipartFile("file", "duplicate.png", "image/png", createImage("png")));

        assertThrows(DuplicateFileException.class, () -> uploadService.uploadImage(request));

        assertNull(repositoryStub.savedAsset);
        assertDirectoryEmpty(uploadRoot.resolve("temp"));
        assertDirectoryEmpty(uploadRoot.resolve("images"));
    }

    @Test
    void removesCommittedFileWhenPersistenceFails() throws Exception {
        repositoryStub.saveFailure = new IllegalStateException("database unavailable");
        AssetUploadRequest request = request(
                "Image",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        assertThrows(IllegalStateException.class, () -> uploadService.uploadImage(request));

        assertDirectoryEmpty(uploadRoot.resolve("temp"));
        assertDirectoryEmpty(uploadRoot.resolve("images"));
    }

    @Test
    void failsImageUploadAndCleansTempWhenPerceptualHashingFails() throws Exception {
        PerceptualHashService failingHashService = new PerceptualHashService() {
            @Override
            public String calculate(java.io.InputStream imageStream) {
                throw new VerificationException("simulated pHash failure");
            }

            @Override
            public int hammingDistance(String hashA, String hashB) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isValidHash(String hash) {
                return false;
            }
        };
        AssetUploadServiceImpl failingUploadService = new AssetUploadServiceImpl(
                repositoryStub.createProxy(),
                new UploadFileValidator(uploadProperties),
                new LocalAssetStorageService(uploadProperties),
                new Sha256ServiceImpl(),
                failingHashService);
        AssetUploadRequest request = request(
                "Image",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        assertThrows(VerificationException.class,
                () -> failingUploadService.uploadImage(request));

        assertNull(repositoryStub.savedAsset);
        assertDirectoryEmpty(uploadRoot.resolve("temp"));
        assertDirectoryEmpty(uploadRoot.resolve("images"));
    }

    @Test
    void translatesSha256UniquenessRaceAndRemovesCommittedFile() throws Exception {
        repositoryStub.saveFailure = new DataIntegrityViolationException(
                "UNIQUE constraint failed: digital_assets.sha256_hash");
        AssetUploadRequest request = request(
                "Racing duplicate",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        assertThrows(DuplicateFileException.class, () -> uploadService.uploadImage(request));

        assertDirectoryEmpty(uploadRoot.resolve("temp"));
        assertDirectoryEmpty(uploadRoot.resolve("images"));
    }

    @Test
    void validatesMetadataBeforeCreatingStorageResources() throws Exception {
        AssetUploadRequest request = request(
                " ",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        assertThrows(BadRequestException.class, () -> uploadService.uploadImage(request));

        assertDirectoryEmpty(uploadRoot.resolve("temp"));
        assertDirectoryEmpty(uploadRoot.resolve("images"));
    }

    @Test
    void requiresAuthenticatedJwtPrincipalBeforeProcessingUpload() throws Exception {
        SecurityContextHolder.clearContext();
        AssetUploadRequest request = request(
                "Image",
                null,
                new MockMultipartFile("file", "image.png", "image/png", createImage("png")));

        assertThrows(UnauthorizedException.class, () -> uploadService.uploadImage(request));

        assertNull(repositoryStub.savedAsset);
        assertDirectoryEmpty(uploadRoot.resolve("temp"));
    }

    private void assertUploadVerification(DigitalAsset asset) {
        assertEquals(DigitalAsset.VerificationStatus.PENDING, asset.getVerificationStatus());
        assertEquals(1, asset.getVerificationHistory().size());
        VerificationHistory history = asset.getVerificationHistory().getFirst();
        assertSame(asset, history.getAsset());
        assertSame(currentUser, history.getVerifiedBy());
        assertEquals(VerificationHistory.VerificationMethod.SHA256_UPLOAD, history.getVerificationMethod());
        assertEquals(VerificationHistory.Result.VERIFIED, history.getResult());
        assertNotNull(history.getVerifiedAt());
    }

    private AssetUploadRequest request(String title, String description, MockMultipartFile file) {
        return AssetUploadRequest.builder()
                .title(title)
                .description(description)
                .file(file)
                .build();
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(7L);
        user.setUuid("6bd552bc-f109-4f98-a595-440054320951");
        user.setFullName("Asset Owner");
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private byte[] createImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException("Test image format is unavailable: " + format);
        }
        return output.toByteArray();
    }

    private byte[] createPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(output);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private void assertDirectoryEmpty(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    private static final class RepositoryStub implements InvocationHandler {

        private boolean duplicate;
        private RuntimeException saveFailure;
        private DigitalAsset savedAsset;

        private DigitalAssetRepository createProxy() {
            return (DigitalAssetRepository) Proxy.newProxyInstance(
                    DigitalAssetRepository.class.getClassLoader(),
                    new Class<?>[]{DigitalAssetRepository.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "existsBySha256Hash" -> duplicate;
                case "saveAndFlush" -> save(arguments[0]);
                case "toString" -> "DigitalAssetRepositoryStub";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private DigitalAsset save(Object entity) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            savedAsset = (DigitalAsset) entity;
            return savedAsset;
        }
    }
}
