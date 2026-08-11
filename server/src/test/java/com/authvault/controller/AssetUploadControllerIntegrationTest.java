package com.authvault.controller;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.repository.DigitalAssetRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(jsonPath("$.data.verificationHistory.length()").value(2))
                .andExpect(jsonPath("$.data.verificationHistory[0].verificationMethod")
                        .value("SHA256_INTEGRITY"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist());
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
