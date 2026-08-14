package com.authvault.controller;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.DocumentRepository;
import com.authvault.repository.UserRepository;
import com.authvault.repository.VerificationHistoryRepository;
import com.authvault.security.jwt.JwtService;
import com.authvault.security.user.CustomUserDetails;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.root=WARN",
        "debug=false"
})
@AutoConfigureMockMvc
@Transactional
class AssetUploadControllerIntegrationTest {

    private static final Path TEST_ROOT = createTestRoot();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DigitalAssetRepository digitalAssetRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private VerificationHistoryRepository verificationHistoryRepository;

    @Autowired
    private JwtService jwtService;

    private String authorizationHeader;
    private User currentUser;

    @DynamicPropertySource
    static void configureTestStorage(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + TEST_ROOT.resolve("integration.db"));
        registry.add("authvault.upload.root-directory",
                () -> TEST_ROOT.resolve("uploads").toString());
    }

    @BeforeEach
    void setUpAuthenticatedUser() {
        currentUser = createUser();

        authorizationHeader = tokenFor(currentUser);
    }

    private User createUser() {
        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setFullName("Upload Owner");
        user.setUsername("owner-" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.com");
        user.setPasswordHash("test-password-hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setVerified(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    @Test
    void uploadsPng() throws Exception {
        byte[] content = createImage("png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "text/plain", content);

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("title", "Diagram")
                        .param("description", "A PNG upload")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data.title").value("Diagram"))
                .andExpect(jsonPath("$.data.assetType").value("IMAGE"))
                .andExpect(jsonPath("$.data.originalFilename").value("diagram.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSize").value(content.length))
                .andExpect(jsonPath("$.data.sha256Hash", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.uploadDate").exists())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedFilename").doesNotExist());

        DigitalAsset stored = digitalAssetRepository
                .findByCurrentOwnerOrderByUploadDateDesc(currentUser).getFirst();
        assertTrue(stored.getPerceptualHash().matches("[0-9a-f]{16}"));
    }

    @Test
    void uploadsJpeg() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpeg", "application/octet-stream", createImage("jpg"));

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("title", "Photo")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assetType").value("IMAGE"))
                .andExpect(jsonPath("$.data.originalFilename").value("photo.jpeg"))
                .andExpect(jsonPath("$.data.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        DigitalAsset stored = digitalAssetRepository
                .findByCurrentOwnerOrderByUploadDateDesc(currentUser).getFirst();
        assertTrue(stored.getPerceptualHash().matches("[0-9a-f]{16}"));
    }

    @Test
    void uploadsPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "image/png", createPdf());

        mockMvc.perform(multipart("/api/v1/assets/documents")
                        .file(file)
                        .param("title", "Report")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assetType").value("DOCUMENT"))
                .andExpect(jsonPath("$.data.originalFilename").value("report.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        DigitalAsset stored = digitalAssetRepository
                .findByCurrentOwnerOrderByUploadDateDesc(currentUser).getFirst();
        org.junit.jupiter.api.Assertions.assertNull(stored.getPerceptualHash());
    }

    @Test
    void rejectsMissingJwt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", createImage("png"));

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("title", "Diagram"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/assets/images"));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("title", "Empty")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsFakePng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes());

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("title", "Fake PNG")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCorruptPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "%PDF-1.7\nnot-a-pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/assets/documents")
                        .file(file)
                        .param("title", "Corrupt PDF")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsDuplicateUploadWithStableErrorCode() throws Exception {
        byte[] content = createImage("png");

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(new MockMultipartFile("file", "first.png", "image/png", content))
                        .param("title", "First")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(new MockMultipartFile("file", "second.png", "image/png", content))
                        .param("title", "Second")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"));
    }

    @Test
    void verifiesUntouchedStoredFileAndCreatesIntegrityHistory() throws Exception {
        String assetId = uploadPngAndReturnAssetId("untouched.png", "Untouched");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        String originalHash = asset.getSha256Hash();

        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.originalHash").value(originalHash))
                .andExpect(jsonPath("$.data.currentHash").value(originalHash))
                .andExpect(jsonPath("$.data.hashMatches").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedAt").exists())
                .andExpect(jsonPath("$.data.id").doesNotExist());

        assertIntegrityHistory(asset, VerificationHistory.Result.VERIFIED,
                "Stored file hash matches the upload hash");
    }

    @Test
    void rejectsManuallyModifiedStoredBytes() throws Exception {
        String assetId = uploadPngAndReturnAssetId("modified.png", "Modified");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Files.writeString(
                TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath()),
                "manually modified bytes");

        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.currentHash", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.data.hashMatches").value(false))
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

        assertEquals(DigitalAsset.VerificationStatus.REJECTED, asset.getVerificationStatus());
        assertIntegrityHistory(asset, VerificationHistory.Result.REJECTED,
                "Stored file hash does not match the upload hash");
    }

    @Test
    void doesNotAllowAnotherUserToVerifyPrivateAsset() throws Exception {
        String assetId = uploadPngAndReturnAssetId("private.png", "Private");
        String otherUserHeader = tokenFor(createUser());

        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", assetId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsNotFoundForNonexistentAssetUuid() throws Exception {
        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordsRejectedResultWhenStoredFileIsMissing() throws Exception {
        String assetId = uploadPngAndReturnAssetId("missing.png", "Missing");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Files.delete(TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath()));

        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentHash").doesNotExist())
                .andExpect(jsonPath("$.data.hashMatches").value(false))
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

        assertIntegrityHistory(asset, VerificationHistory.Result.REJECTED,
                "Stored file could not be read");
    }

    @Test
    void listsOnlyCurrentUsersAssetsNewestFirst() throws Exception {
        String olderAssetId = uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile("file", "older.png", "image/png", createImage("png")),
                "Older",
                authorizationHeader);
        String newerAssetId = uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile("file", "newer.jpg", "image/jpeg", createImage("jpg")),
                "Newer",
                authorizationHeader);

        DigitalAsset olderAsset = digitalAssetRepository.findByUuid(olderAssetId).orElseThrow();
        DigitalAsset newerAsset = digitalAssetRepository.findByUuid(newerAssetId).orElseThrow();
        olderAsset.setUploadDate(LocalDateTime.now().minusDays(1));
        newerAsset.setUploadDate(LocalDateTime.now());
        digitalAssetRepository.saveAllAndFlush(java.util.List.of(olderAsset, newerAsset));

        String otherUserHeader = tokenFor(createUser());
        uploadAndReturnAssetId(
                "/api/v1/assets/documents",
                new MockMultipartFile("file", "other.pdf", "application/pdf", createPdf()),
                "Other user's asset",
                otherUserHeader);

        mockMvc.perform(get("/api/v1/assets")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].assetId").value(newerAssetId))
                .andExpect(jsonPath("$.data[0].title").value("Newer"))
                .andExpect(jsonPath("$.data[1].assetId").value(olderAssetId))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist());
    }

    @Test
    void listsOldestAssetsFirstWhenRequested() throws Exception {
        DigitalAsset older = createAsset(
                currentUser, "Older", "older.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now().minusDays(2));
        DigitalAsset newer = createAsset(
                currentUser, "Newer", "newer.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.PENDING, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets")
                        .param("sort", "oldest")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetId").value(older.getUuid()))
                .andExpect(jsonPath("$.data[1].assetId").value(newer.getUuid()));
    }

    @Test
    void filtersAssetsByImageAndDocumentType() throws Exception {
        DigitalAsset image = createAsset(
                currentUser, "Image", "image.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        DigitalAsset document = createAsset(
                currentUser, "Document", "document.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets")
                        .param("type", "IMAGE")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(image.getUuid()));

        mockMvc.perform(get("/api/v1/assets")
                        .param("type", "DOCUMENT")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(document.getUuid()));
    }

    @Test
    void filtersAssetsByVerificationStatus() throws Exception {
        DigitalAsset rejected = createAsset(
                currentUser, "Rejected", "rejected.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.REJECTED, LocalDateTime.now());
        createAsset(
                currentUser, "Verified", "verified.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets")
                        .param("status", "REJECTED")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(rejected.getUuid()));
    }

    @Test
    void searchesTitleAndOriginalFilenameCaseInsensitively() throws Exception {
        DigitalAsset match = createAsset(
                currentUser, "Quarterly Evidence", "financial-SUMMARY.pdf",
                DigitalAsset.AssetType.DOCUMENT, DigitalAsset.VerificationStatus.VERIFIED,
                LocalDateTime.now());
        createAsset(
                currentUser, "Unrelated", "other.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets")
                        .param("search", "  quarterly  ")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(match.getUuid()));

        mockMvc.perform(get("/api/v1/assets")
                        .param("search", "summary")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(match.getUuid()));

        mockMvc.perform(get("/api/v1/assets")
                        .param("search", "qUaRtErLy")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(match.getUuid()));
    }

    @Test
    void combinesListFiltersAndTreatsBlankSearchAsAbsent() throws Exception {
        DigitalAsset match = createAsset(
                currentUser, "Evidence image", "evidence.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        createAsset(
                currentUser, "Evidence document", "evidence.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        createAsset(
                currentUser, "Rejected evidence", "rejected.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.REJECTED, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets")
                        .param("type", "IMAGE")
                        .param("status", "VERIFIED")
                        .param("search", "evidence")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].assetId").value(match.getUuid()));

        mockMvc.perform(get("/api/v1/assets")
                        .param("search", "   ")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void rejectsInvalidListQueryParameters() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .param("type", "image")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/assets")
                        .param("status", "APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/assets")
                        .param("sort", "recent")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsOwnedAssetDetailWithVerificationHistory() throws Exception {
        String assetId = uploadPngAndReturnAssetId("detail.png", "Detail");
        mockMvc.perform(post("/api/v1/assets/{assetId}/verify-integrity", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.title").value("Detail"))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.assetType").value("IMAGE"))
                .andExpect(jsonPath("$.data.originalFilename").value("detail.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSize").isNumber())
                .andExpect(jsonPath("$.data.sha256Hash", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.uploadDate").exists())
                .andExpect(jsonPath("$.data.lastVerifiedAt").exists())
                .andExpect(jsonPath("$.data.verificationHistory.length()").value(2))
                .andExpect(jsonPath("$.data.verificationHistory[0].verificationMethod")
                        .value("SHA256_INTEGRITY"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist());
    }

    @Test
    void assetDetailUsesNewestHistoryTimestampAsLastVerifiedAt() throws Exception {
        DigitalAsset asset = createAsset(
                currentUser, "Detail timestamps", "timestamps.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        LocalDateTime olderTimestamp = LocalDateTime.of(2026, 1, 2, 10, 0, 1);
        LocalDateTime newestTimestamp = LocalDateTime.of(2026, 2, 3, 11, 30, 2);
        createHistory(asset, newestTimestamp, VerificationHistory.Result.VERIFIED, "Newest safe note");
        createHistory(asset, olderTimestamp, VerificationHistory.Result.REJECTED, "Older safe note");

        mockMvc.perform(get("/api/v1/assets/{assetId}", asset.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastVerifiedAt").value(newestTimestamp.toString()));
    }

    @Test
    void assetDetailHasNullLastVerifiedAtWhenHistoryIsEmpty() throws Exception {
        DigitalAsset asset = createAsset(
                currentUser, "No history", "no-history.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.PENDING, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets/{assetId}", asset.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastVerifiedAt").doesNotExist())
                .andExpect(jsonPath("$.data.verificationHistory.length()").value(0));
    }

    @Test
    void ownerRetrievesSafeVerificationHistoryNewestFirst() throws Exception {
        DigitalAsset asset = createAsset(
                currentUser, "History", "history.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        LocalDateTime olderTimestamp = LocalDateTime.of(2026, 3, 1, 9, 0, 1);
        LocalDateTime newestTimestamp = LocalDateTime.of(2026, 3, 2, 9, 0, 2);
        createHistory(asset, olderTimestamp, VerificationHistory.Result.REJECTED, "Older safe note");
        createHistory(asset, newestTimestamp, VerificationHistory.Result.VERIFIED, "Newest safe note");

        mockMvc.perform(get("/api/v1/assets/{assetId}/verification-history", asset.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].verificationMethod").value("SHA256_INTEGRITY"))
                .andExpect(jsonPath("$.data[0].result").value("VERIFIED"))
                .andExpect(jsonPath("$.data[0].verifiedAt").value(newestTimestamp.toString()))
                .andExpect(jsonPath("$.data[0].notes").value("Newest safe note"))
                .andExpect(jsonPath("$.data[1].verifiedAt").value(olderTimestamp.toString()))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].assetId").doesNotExist())
                .andExpect(jsonPath("$.data[0].verifiedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist());
    }

    @Test
    void ownerRetrievesEmptyVerificationHistory() throws Exception {
        DigitalAsset asset = createAsset(
                currentUser, "Empty history", "empty-history.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.PENDING, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets/{assetId}/verification-history", asset.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void otherUserCannotRetrieveVerificationHistory() throws Exception {
        DigitalAsset asset = createAsset(
                currentUser, "Private history", "private-history.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        createHistory(asset, LocalDateTime.now(), VerificationHistory.Result.VERIFIED, "Safe note");

        mockMvc.perform(get("/api/v1/assets/{assetId}/verification-history", asset.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(createUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void nonexistentAssetHasNoVerificationHistory() throws Exception {
        mockMvc.perform(get("/api/v1/assets/{assetId}/verification-history", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void preventsOtherUserFromReadingOrDownloadingPrivateAsset() throws Exception {
        String assetId = uploadPngAndReturnAssetId("private-read.png", "Private read");
        String otherUserHeader = tokenFor(createUser());

        mockMvc.perform(get("/api/v1/assets")
                        .header(HttpHeaders.AUTHORIZATION, otherUserHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserHeader))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/assets/{assetId}/download", assetId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamsOwnedDownloadWithSafeNoStoreHeaders() throws Exception {
        byte[] content = createImage("png");
        String assetId = uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile(
                        "file", "../../private\\download.png", "text/plain", content),
                "Download",
                authorizationHeader);

        mockMvc.perform(get("/api/v1/assets/{assetId}/download", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(content))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        allOf(
                                containsString("attachment"),
                                containsString("download.png"),
                                not(containsString("..")),
                                not(containsString("private")))));
    }

    @Test
    void requiresJwtForOwnedAssetReadApis() throws Exception {
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/assets/{assetId}/verification-history", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsOnlyPreviousOwnedImagesRankedByPerceptualDistance() throws Exception {
        LocalDateTime targetTime = LocalDateTime.now();
        DigitalAsset target = createAsset(
                currentUser, "Target", "target.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime);
        DigitalAsset closest = createAsset(
                currentUser, "Closest", "closest.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime.minusDays(1));
        DigitalAsset farther = createAsset(
                currentUser, "Farther", "farther.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime.minusDays(2));
        DigitalAsset document = createAsset(
                currentUser, "Document", "document.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime.minusDays(3));
        DigitalAsset future = createAsset(
                currentUser, "Future", "future.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime.plusDays(1));
        DigitalAsset otherUsersImage = createAsset(
                createUser(), "Other", "other.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, targetTime.minusDays(1));

        target.setPerceptualHash("0000000000000000");
        closest.setPerceptualHash("0000000000000001");
        farther.setPerceptualHash("0000000000000007");
        document.setPerceptualHash(null);
        future.setPerceptualHash("0000000000000000");
        otherUsersImage.setPerceptualHash("0000000000000000");
        digitalAssetRepository.saveAllAndFlush(java.util.List.of(
                target, closest, farther, document, future, otherUsersImage));

        mockMvc.perform(get("/api/v1/assets/{assetId}/similar-images", target.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(target.getUuid()))
                .andExpect(jsonPath("$.data.perceptualHash").value("0000000000000000"))
                .andExpect(jsonPath("$.data.closestMatches.length()").value(2))
                .andExpect(jsonPath("$.data.closestMatches[0].matchedAssetId")
                        .value(closest.getUuid()))
                .andExpect(jsonPath("$.data.closestMatches[0].hammingDistance").value(1))
                .andExpect(jsonPath("$.data.closestMatches[0].matchBand")
                        .value("NEAR_DUPLICATE"))
                .andExpect(jsonPath("$.data.closestMatches[1].matchedAssetId")
                        .value(farther.getUuid()))
                .andExpect(jsonPath("$.data.closestMatches[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.closestMatches[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.closestMatches[0].storedFilename").doesNotExist());
    }

    @Test
    void similarImageSearchEnforcesOwnerImageAndExistenceRules() throws Exception {
        DigitalAsset image = createAsset(
                currentUser, "Private image", "private.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        image.setPerceptualHash("0000000000000000");
        digitalAssetRepository.saveAndFlush(image);
        DigitalAsset document = createAsset(
                currentUser, "Document", "document.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());

        mockMvc.perform(get("/api/v1/assets/{assetId}/similar-images", image.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(createUser())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/assets/{assetId}/similar-images", document.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/assets/{assetId}/similar-images", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanRequestKnownOriginalComparisonWithClosestPlausibleCandidate() throws Exception {
        String candidateId = uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile("file", "reference.jpg", "image/jpeg", createImage("jpg")),
                "Reference",
                authorizationHeader);
        String targetId = uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile("file", "target.png", "image/png", createImage("png")),
                "Target",
                authorizationHeader);
        DigitalAsset candidate = digitalAssetRepository.findByUuid(candidateId).orElseThrow();
        DigitalAsset target = digitalAssetRepository.findByUuid(targetId).orElseThrow();
        candidate.setUploadDate(LocalDateTime.now().minusDays(1));
        target.setUploadDate(LocalDateTime.now());
        candidate.setPerceptualHash("0000000000000000");
        target.setPerceptualHash("0000000000000000");
        digitalAssetRepository.saveAllAndFlush(java.util.List.of(candidate, target));

        mockMvc.perform(post("/api/v1/assets/{assetId}/compare-known-original", targetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(targetId))
                .andExpect(jsonPath("$.data.candidateFound").value(true))
                .andExpect(jsonPath("$.data.candidate.assetId").value(candidateId))
                .andExpect(jsonPath("$.data.candidate.matchBand")
                        .value("EXACT_VISUAL_HASH"))
                .andExpect(jsonPath("$.data.comparisonPerformed").value(false))
                .andExpect(jsonPath("$.data.reason").value("AI_SERVICE_DISABLED"))
                .andExpect(jsonPath("$.data.candidate.id").doesNotExist())
                .andExpect(jsonPath("$.data.candidate.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.candidate.storedFilename").doesNotExist());
    }

    @Test
    void knownOriginalComparisonReturnsControlledNoCandidateAndEnforcesTargetAccess() throws Exception {
        DigitalAsset target = createAsset(
                currentUser, "No original", "no-original.png", DigitalAsset.AssetType.IMAGE,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        target.setPerceptualHash("0000000000000000");
        digitalAssetRepository.saveAndFlush(target);

        mockMvc.perform(post("/api/v1/assets/{assetId}/compare-known-original", target.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateFound").value(false))
                .andExpect(jsonPath("$.data.comparisonPerformed").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_KNOWN_ORIGINAL"));

        mockMvc.perform(post("/api/v1/assets/{assetId}/compare-known-original", target.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(createUser())))
                .andExpect(status().isNotFound());

        DigitalAsset document = createAsset(
                currentUser, "Document", "document.pdf", DigitalAsset.AssetType.DOCUMENT,
                DigitalAsset.VerificationStatus.VERIFIED, LocalDateTime.now());
        mockMvc.perform(post("/api/v1/assets/{assetId}/compare-known-original", document.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/assets/{assetId}/compare-known-original", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanRequestAiAnalysisAndDisabledServiceIsSafelyReported() throws Exception {
        String assetId = uploadPngAndReturnAssetId("ai-analysis.png", "AI analysis");

        mockMvc.perform(post("/api/v1/assets/{assetId}/analyze-ai", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(assetId))
                .andExpect(jsonPath("$.data.aiGenerationAnalysis.performed").value(false))
                .andExpect(jsonPath("$.data.aiGenerationAnalysis.status")
                        .value("AI_SERVICE_DISABLED"))
                .andExpect(jsonPath("$.data.manipulationAnalysis.performed").value(false))
                .andExpect(jsonPath("$.data.manipulationAnalysis.status")
                        .value("AI_SERVICE_DISABLED"))
                .andExpect(jsonPath("$.data.analyzedAt").exists())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedFilename").doesNotExist())
                .andExpect(jsonPath("$.data.rawLogit").doesNotExist());
    }

    @Test
    void aiAnalysisRequiresAuthenticationOwnershipImageTypeAndExistingUuid() throws Exception {
        String imageId = uploadPngAndReturnAssetId("private-ai.png", "Private AI");
        String documentId = uploadAndReturnAssetId(
                "/api/v1/assets/documents",
                new MockMultipartFile("file", "ai-document.pdf", "application/pdf", createPdf()),
                "AI document",
                authorizationHeader);

        mockMvc.perform(post("/api/v1/assets/{assetId}/analyze-ai", imageId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/assets/{assetId}/analyze-ai", imageId)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(createUser())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/assets/{assetId}/analyze-ai", documentId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/assets/{assetId}/analyze-ai", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerDeletesImageAssetHistoryAndPhysicalFile() throws Exception {
        String assetId = uploadPngAndReturnAssetId("delete-image.png", "Delete image");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Long databaseId = asset.getId();
        Path storedFile = TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath());

        mockMvc.perform(delete("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(digitalAssetRepository.findByUuid(assetId).isEmpty());
        assertTrue(verificationHistoryRepository.findByAssetId(databaseId).isEmpty());
        assertTrue(Files.notExists(storedFile));
    }

    @Test
    void ownerDeletesDocumentAssetAndDependentDocument() throws Exception {
        String assetId = uploadAndReturnAssetId(
                "/api/v1/assets/documents",
                new MockMultipartFile("file", "delete-document.pdf", "application/pdf", createPdf()),
                "Delete document",
                authorizationHeader);
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Long databaseId = asset.getId();
        Path storedFile = TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath());
        assertTrue(documentRepository.findByAssetId(databaseId).isPresent());

        mockMvc.perform(delete("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(digitalAssetRepository.findByUuid(assetId).isEmpty());
        assertTrue(documentRepository.findByAssetId(databaseId).isEmpty());
        assertTrue(verificationHistoryRepository.findByAssetId(databaseId).isEmpty());
        assertTrue(Files.notExists(storedFile));
    }

    @Test
    void anotherUserCannotDeleteOwnedAsset() throws Exception {
        String assetId = uploadPngAndReturnAssetId("delete-private.png", "Delete private");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Path storedFile = TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath());

        mockMvc.perform(delete("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(createUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertTrue(digitalAssetRepository.findByUuid(assetId).isPresent());
        assertTrue(Files.exists(storedFile));
    }

    @Test
    void deletingNonexistentAssetReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/assets/{assetId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deletesDatabaseAssetWhenPhysicalFileIsAlreadyMissing() throws Exception {
        String assetId = uploadPngAndReturnAssetId("already-missing.png", "Already missing");
        DigitalAsset asset = digitalAssetRepository.findByUuid(assetId).orElseThrow();
        Files.delete(TEST_ROOT.resolve("uploads").resolve(asset.getStoragePath()));

        mockMvc.perform(delete("/api/v1/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(digitalAssetRepository.findByUuid(assetId).isEmpty());
    }

    @Test
    void deletingOwnedAssetDoesNotDeleteAnotherUsersAssetOrFile() throws Exception {
        String ownedAssetId = uploadPngAndReturnAssetId("owned-delete.png", "Owned delete");
        String otherUserHeader = tokenFor(createUser());
        String otherAssetId = uploadAndReturnAssetId(
                "/api/v1/assets/documents",
                new MockMultipartFile("file", "other-kept.pdf", "application/pdf", createPdf()),
                "Other kept",
                otherUserHeader);
        DigitalAsset otherAsset = digitalAssetRepository.findByUuid(otherAssetId).orElseThrow();
        Path otherFile = TEST_ROOT.resolve("uploads").resolve(otherAsset.getStoragePath());

        mockMvc.perform(delete("/api/v1/assets/{assetId}", ownedAssetId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isNoContent());

        assertTrue(digitalAssetRepository.findByUuid(ownedAssetId).isEmpty());
        assertTrue(digitalAssetRepository.findByUuid(otherAssetId).isPresent());
        assertTrue(Files.exists(otherFile));
    }

    @Test
    void deleteRequiresJwt() throws Exception {
        mockMvc.perform(delete("/api/v1/assets/{assetId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private DigitalAsset createAsset(
            User owner,
            String title,
            String originalFilename,
            DigitalAsset.AssetType assetType,
            DigitalAsset.VerificationStatus verificationStatus,
            LocalDateTime uploadDate) {
        String uuid = UUID.randomUUID().toString();
        String extension = assetType == DigitalAsset.AssetType.IMAGE ? "png" : "pdf";
        String directory = assetType == DigitalAsset.AssetType.IMAGE ? "images" : "documents";
        String storedFilename = uuid + "." + extension;
        String hashSeed = UUID.randomUUID().toString().replace("-", "");

        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(uuid);
        asset.setOwner(owner);
        asset.setCurrentOwner(owner);
        asset.setTitle(title);
        asset.setAssetType(assetType);
        asset.setOriginalFilename(originalFilename);
        asset.setStoredFilename(storedFilename);
        asset.setMimeType(assetType == DigitalAsset.AssetType.IMAGE
                ? "image/png"
                : "application/pdf");
        asset.setFileSize(128L);
        asset.setStoragePath(directory + "/" + storedFilename);
        asset.setSha256Hash(hashSeed + hashSeed);
        asset.setUploadDate(uploadDate);
        asset.setVerificationStatus(verificationStatus);
        return digitalAssetRepository.saveAndFlush(asset);
    }

    private VerificationHistory createHistory(
            DigitalAsset asset,
            LocalDateTime verifiedAt,
            VerificationHistory.Result result,
            String notes) {
        VerificationHistory history = new VerificationHistory();
        history.setAsset(asset);
        history.setVerifiedBy(asset.getCurrentOwner());
        history.setVerificationMethod(VerificationHistory.VerificationMethod.SHA256_INTEGRITY);
        history.setResult(result);
        history.setVerifiedAt(verifiedAt);
        history.setNotes(notes);
        return verificationHistoryRepository.saveAndFlush(history);
    }

    private String uploadPngAndReturnAssetId(String filename, String title) throws Exception {
        return uploadAndReturnAssetId(
                "/api/v1/assets/images",
                new MockMultipartFile("file", filename, "image/png", createImage("png")),
                title,
                authorizationHeader);
    }

    private String uploadAndReturnAssetId(
            String endpoint,
            MockMultipartFile file,
            String title,
            String authHeader) throws Exception {
        String response = mockMvc.perform(multipart(endpoint)
                        .file(file)
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.assetId");
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private void assertIntegrityHistory(
            DigitalAsset asset,
            VerificationHistory.Result expectedResult,
            String expectedNotes) {
        var integrityHistory = verificationHistoryRepository.findByAsset(asset).stream()
                .filter(history -> history.getVerificationMethod()
                        == VerificationHistory.VerificationMethod.SHA256_INTEGRITY)
                .toList();

        assertEquals(1, integrityHistory.size());
        assertEquals(expectedResult, integrityHistory.getFirst().getResult());
        assertEquals(expectedNotes, integrityHistory.getFirst().getNotes());
        assertEquals(currentUser, integrityHistory.getFirst().getVerifiedBy());
        assertNotNull(integrityHistory.getFirst().getVerifiedAt());
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

    private static Path createTestRoot() {
        try {
            return Files.createTempDirectory("authvault-upload-controller-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
