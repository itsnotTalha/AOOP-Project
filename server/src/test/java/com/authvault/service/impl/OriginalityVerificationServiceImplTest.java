package com.authvault.service.impl;

import com.authvault.blockchain.OriginalRegistryClient;
import com.authvault.blockchain.OriginalRegistryClientException;
import com.authvault.client.comparison.ComparisonClientResult;
import com.authvault.client.comparison.ImageComparisonClient;
import com.authvault.config.UploadProperties;
import com.authvault.dto.comparison.ImageComparisonResponse;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.originality.OriginalityOutcome;
import com.authvault.dto.originality.OriginalityReasonCode;
import com.authvault.dto.originality.OriginalityVerificationResponse;
import com.authvault.entity.User;
import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.exception.MalformedFileException;
import com.authvault.exception.UnsupportedFileTypeException;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.AssetStorageService;
import com.authvault.service.RegisteredOriginalCandidateService;
import com.authvault.validation.UploadFileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OriginalityVerificationServiceImplTest {

    @TempDir
    Path testDirectory;

    private UploadProperties uploadProperties;
    private LocalAssetStorageService storageService;
    private Sha256ServiceImpl sha256Service;
    private OriginalRegistryClient registryClient;
    private RegisteredOriginalCandidateService candidateService;
    private ImageComparisonClient imageComparisonClient;
    private OriginalityVerificationServiceImpl service;
    private User checker;

    @BeforeEach
    void setUp() {
        uploadProperties = new UploadProperties();
        uploadProperties.setRootDirectory(testDirectory.resolve("uploads"));
        storageService = new LocalAssetStorageService(uploadProperties);
        sha256Service = new Sha256ServiceImpl();
        registryClient = mock(OriginalRegistryClient.class);
        candidateService = mock(RegisteredOriginalCandidateService.class);
        imageComparisonClient = mock(ImageComparisonClient.class);
        service = new OriginalityVerificationServiceImpl(
                new UploadFileValidator(uploadProperties),
                storageService,
                sha256Service,
                new PerceptualHashServiceImpl(),
                registryClient,
                candidateService,
                imageComparisonClient);
        when(registryClient.isEnabled()).thenReturn(true);

        checker = authenticatedUser();
        CustomUserDetails userDetails = new CustomUserDetails(checker);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exactFabricShaMatchHasPriorityAndSkipsPHashAndComparison() throws Exception {
        byte[] submitted = imageBytes(Color.BLUE, false);
        String submittedSha = sha256(submitted);
        BlockchainOriginalResponse ledgerOriginal = ledgerOriginal(
                UUID.randomUUID().toString(), submittedSha);
        when(registryClient.findBySha256(submittedSha))
                .thenReturn(Optional.of(ledgerOriginal));

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(
                OriginalityOutcome.EXACT_REGISTERED_ORIGINAL);
        assertThat(response.blockchainLookup().status()).isEqualTo("COMPLETED");
        assertThat(response.blockchainLookup().exactMatch()).isTrue();
        assertThat(response.submitted().sha256()).isEqualTo(submittedSha);
        assertThat(response.submitted().pHash()).isNull();
        assertThat(response.reasonCodes())
                .containsExactly(OriginalityReasonCode.EXACT_SHA256_MATCH);
        verify(candidateService, never()).findPlausibleCandidates(any());
        verify(imageComparisonClient, never()).compareImages(any(), any(), any(), any());
        assertTempDirectoryEmpty();
    }

    @Test
    void confirmedStrongCandidateWithReliableChangesIsModifiedAndUsesOriginalAsReference()
            throws Exception {
        byte[] submitted = imageBytes(Color.BLUE, true);
        byte[] originalBytes = imageBytes(Color.BLUE, false);
        ConfirmedSetup setup = configureConfirmedCandidate(
                submitted, originalBytes, PerceptualMatchBand.NEAR_DUPLICATE, 4);
        AtomicReference<byte[]> sentReference = new AtomicReference<>();
        AtomicReference<byte[]> sentTarget = new AtomicReference<>();
        when(imageComparisonClient.compareImages(any(), any(), any(), any())).thenAnswer(invocation -> {
            sentReference.set(((Resource) invocation.getArgument(0))
                    .getInputStream().readAllBytes());
            sentTarget.set(((Resource) invocation.getArgument(2))
                    .getInputStream().readAllBytes());
            return ComparisonClientResult.success(comparison("ALIGNED", 0.084));
        });

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(
                OriginalityOutcome.MODIFIED_REGISTERED_ORIGINAL);
        assertThat(response.registeredOriginal().assetId())
                .isEqualTo(setup.candidate().assetId());
        assertThat(response.similarity().pHashDistance()).isEqualTo(4);
        assertThat(response.comparison().changedAreaRatio()).isEqualTo(0.084);
        assertThat(response.reasonCodes()).contains(
                OriginalityReasonCode.REGISTERED_SOURCE_FOUND,
                OriginalityReasonCode.SHA256_DIFFERENT,
                OriginalityReasonCode.PHASH_NEAR_DUPLICATE,
                OriginalityReasonCode.MEANINGFUL_CHANGES_DETECTED);
        assertThat(sentReference.get()).isEqualTo(originalBytes);
        assertThat(sentTarget.get()).isEqualTo(submitted);
        assertTempDirectoryEmpty();
    }

    @Test
    void lowReliabilityComparisonProducesPossibleDerivative() throws Exception {
        byte[] submitted = imageBytes(Color.GREEN, true);
        byte[] originalBytes = imageBytes(Color.GREEN, false);
        configureConfirmedCandidate(
                submitted, originalBytes, PerceptualMatchBand.NEAR_DUPLICATE, 5);
        when(imageComparisonClient.compareImages(any(), any(), any(), any()))
                .thenReturn(ComparisonClientResult.success(
                        comparison("FALLBACK_RESIZE", 0.15)));

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(OriginalityOutcome.POSSIBLE_DERIVATIVE);
        assertThat(response.reasonCodes())
                .contains(OriginalityReasonCode.COMPARISON_LOW_RELIABILITY);
        assertThat(response.reasonCodes())
                .doesNotContain(OriginalityReasonCode.MEANINGFUL_CHANGES_DETECTED);
        assertTempDirectoryEmpty();
    }

    @Test
    void noFabricConfirmedPHashCandidateMeansNoRegisteredSource() throws Exception {
        byte[] submitted = imageBytes(Color.MAGENTA, true);
        String submittedSha = sha256(submitted);
        RegisteredOriginalCandidateService.Candidate localCandidate =
                new RegisteredOriginalCandidateService.Candidate(
                        UUID.randomUUID().toString(),
                        "d".repeat(64),
                        "images/private.png",
                        2,
                        PerceptualMatchBand.NEAR_DUPLICATE);
        when(registryClient.findBySha256(submittedSha)).thenReturn(Optional.empty());
        when(candidateService.findPlausibleCandidates(any()))
                .thenReturn(List.of(localCandidate));
        when(registryClient.findBySha256(localCandidate.sha256()))
                .thenReturn(Optional.empty());

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(
                OriginalityOutcome.NO_REGISTERED_SOURCE_FOUND);
        assertThat(response.blockchainLookup().status()).isEqualTo("COMPLETED");
        assertThat(response.reasonCodes()).containsExactly(
                OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                OriginalityReasonCode.NO_PLAUSIBLE_REGISTERED_SOURCE);
        verify(imageComparisonClient, never()).compareImages(any(), any(), any(), any());
        assertTempDirectoryEmpty();
    }

    @Test
    void unavailableOrDisabledBlockchainIsInconclusiveAndNeverClaimsNoSource()
            throws Exception {
        byte[] submitted = imageBytes(Color.ORANGE, false);
        String submittedSha = sha256(submitted);
        when(registryClient.findBySha256(submittedSha)).thenThrow(
                new OriginalRegistryClientException(
                        OriginalRegistryClientException.Reason.UNAVAILABLE,
                        "peer unavailable"));

        OriginalityVerificationResponse unavailable = service.verify(upload(submitted));

        assertThat(unavailable.outcome()).isEqualTo(OriginalityOutcome.INCONCLUSIVE);
        assertThat(unavailable.blockchainLookup().status()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.reasonCodes())
                .containsExactly(OriginalityReasonCode.BLOCKCHAIN_UNAVAILABLE);

        when(registryClient.isEnabled()).thenReturn(false);
        OriginalityVerificationResponse disabled = service.verify(upload(submitted));
        assertThat(disabled.outcome()).isEqualTo(OriginalityOutcome.INCONCLUSIVE);
        assertThat(disabled.blockchainLookup().status()).isEqualTo("DISABLED");
        assertThat(disabled.reasonCodes())
                .containsExactly(OriginalityReasonCode.BLOCKCHAIN_DISABLED);
        verify(candidateService, never()).findPlausibleCandidates(any());
        assertTempDirectoryEmpty();
    }

    @Test
    void responseDoesNotExposeCandidateOwnerOrInternalStorageData() throws Exception {
        byte[] submitted = imageBytes(Color.CYAN, true);
        byte[] originalBytes = imageBytes(Color.CYAN, false);
        ConfirmedSetup setup = configureConfirmedCandidate(
                submitted, originalBytes, PerceptualMatchBand.POSSIBLE_MATCH, 10);
        when(imageComparisonClient.compareImages(any(), any(), any(), any()))
                .thenReturn(ComparisonClientResult.success(comparison("ALIGNED", 0.02)));

        OriginalityVerificationResponse response = service.verify(upload(submitted));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertThat(json).doesNotContain(
                checker.getEmail(),
                checker.getUuid(),
                setup.candidate().storageKey(),
                "storedFilename",
                "storagePath",
                "databaseId");
        assertThat(json).contains(setup.ledgerOriginal().creatorIdHash());
    }

    @Test
    void invalidPdfMalformedImageAndOversizedImageAreRejected() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "image", "document.pdf", "application/pdf", "%PDF-1.7".getBytes());
        assertThatThrownBy(() -> service.verify(pdf))
                .isInstanceOf(UnsupportedFileTypeException.class);

        byte[] malformedPng = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        assertThatThrownBy(() -> service.verify(upload(malformedPng)))
                .isInstanceOf(MalformedFileException.class);

        uploadProperties.setImageMaxSize(DataSize.ofBytes(10));
        MockMultipartFile oversized = new MockMultipartFile(
                "image", "large.png", "image/png", new byte[11]);
        assertThatThrownBy(() -> service.verify(oversized))
                .isInstanceOf(FileSizeLimitExceededException.class);
        assertTempDirectoryEmpty();
    }

    @Test
    void comparisonFailureReturnsInconclusiveAndCleansBothTemporaryFiles() throws Exception {
        byte[] submitted = imageBytes(Color.PINK, true);
        byte[] originalBytes = imageBytes(Color.PINK, false);
        configureConfirmedCandidate(
                submitted, originalBytes, PerceptualMatchBand.NEAR_DUPLICATE, 2);
        when(imageComparisonClient.compareImages(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("simulated comparison failure"));

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(OriginalityOutcome.INCONCLUSIVE);
        assertThat(response.comparison().status()).isEqualTo("UNAVAILABLE");
        assertThat(response.reasonCodes())
                .contains(OriginalityReasonCode.COMPARISON_UNAVAILABLE);
        assertTempDirectoryEmpty();
    }

    @Test
    void invalidComparisonMetricsAreInconclusive() throws Exception {
        byte[] submitted = imageBytes(Color.GRAY, true);
        byte[] originalBytes = imageBytes(Color.GRAY, false);
        configureConfirmedCandidate(
                submitted, originalBytes, PerceptualMatchBand.NEAR_DUPLICATE, 2);
        when(imageComparisonClient.compareImages(any(), any(), any(), any()))
                .thenReturn(ComparisonClientResult.success(comparison("ALIGNED", Double.NaN)));

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(OriginalityOutcome.INCONCLUSIVE);
        assertThat(response.reasonCodes())
                .contains(OriginalityReasonCode.COMPARISON_UNAVAILABLE);
        assertTempDirectoryEmpty();
    }

    @Test
    void missingRegisteredContentIsInconclusiveWithoutTryingAnotherSource() throws Exception {
        byte[] submitted = imageBytes(Color.YELLOW, true);
        String submittedSha = sha256(submitted);
        RegisteredOriginalCandidateService.Candidate missing =
                new RegisteredOriginalCandidateService.Candidate(
                        UUID.randomUUID().toString(),
                        "e".repeat(64),
                        "images/missing.png",
                        1,
                        PerceptualMatchBand.NEAR_DUPLICATE);
        BlockchainOriginalResponse ledger = ledgerOriginal(missing.assetId(), missing.sha256());
        when(registryClient.findBySha256(submittedSha)).thenReturn(Optional.empty());
        when(candidateService.findPlausibleCandidates(any())).thenReturn(List.of(missing));
        when(registryClient.findBySha256(missing.sha256())).thenReturn(Optional.of(ledger));

        OriginalityVerificationResponse response = service.verify(upload(submitted));

        assertThat(response.outcome()).isEqualTo(OriginalityOutcome.INCONCLUSIVE);
        assertThat(response.comparison().status())
                .isEqualTo("REGISTERED_ORIGINAL_CONTENT_UNAVAILABLE");
        assertThat(response.reasonCodes())
                .contains(OriginalityReasonCode.REGISTERED_ORIGINAL_CONTENT_UNAVAILABLE);
        verify(imageComparisonClient, never()).compareImages(any(), any(), any(), any());
        assertTempDirectoryEmpty();
    }

    private ConfirmedSetup configureConfirmedCandidate(
            byte[] submitted,
            byte[] originalBytes,
            PerceptualMatchBand band,
            int distance) throws Exception {
        String submittedSha = sha256(submitted);
        String originalSha = sha256(originalBytes);
        AssetStorageService.StoredAsset storedOriginal = storeOriginal(originalBytes);
        RegisteredOriginalCandidateService.Candidate candidate =
                new RegisteredOriginalCandidateService.Candidate(
                        UUID.randomUUID().toString(),
                        originalSha,
                        storedOriginal.storageKey(),
                        distance,
                        band);
        BlockchainOriginalResponse ledgerOriginal =
                ledgerOriginal(candidate.assetId(), originalSha);
        when(registryClient.findBySha256(submittedSha)).thenReturn(Optional.empty());
        when(candidateService.findPlausibleCandidates(any()))
                .thenReturn(List.of(candidate));
        when(registryClient.findBySha256(originalSha))
                .thenReturn(Optional.of(ledgerOriginal));
        return new ConfirmedSetup(candidate, ledgerOriginal);
    }

    private AssetStorageService.StoredAsset storeOriginal(byte[] bytes) throws Exception {
        Path temporaryFile = storageService.createTempFile("png");
        Files.write(temporaryFile, bytes);
        return storageService.commitValidatedImage(temporaryFile, "png");
    }

    private BlockchainOriginalResponse ledgerOriginal(String assetId, String sha256) {
        return new BlockchainOriginalResponse(
                assetId,
                "b".repeat(64),
                "b".repeat(64),
                sha256,
                "IMAGE",
                "VERIFIED",
                "c".repeat(64),
                Instant.parse("2026-08-15T08:00:00Z"));
    }

    private ImageComparisonResponse comparison(String alignmentStatus, double changedAreaRatio) {
        return new ImageComparisonResponse(
                "1",
                new ImageComparisonResponse.Alignment(
                        alignmentStatus,
                        "ALIGNED".equals(alignmentStatus)
                                ? "ORB_HOMOGRAPHY"
                                : "RESIZE",
                        50,
                        48,
                        20,
                        16,
                        0.8),
                new ImageComparisonResponse.Difference(
                        true,
                        changedAreaRatio,
                        12.5,
                        null),
                new ImageComparisonResponse.ChangeMask(
                        true, "PNG", "bWFzaw=="),
                "COMPLETED");
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile(
                "image", "submitted.png", "application/octet-stream", bytes);
    }

    private byte[] imageBytes(Color color, boolean changed) throws Exception {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(color);
        graphics.fillOval(10, 10, 35, 30);
        if (changed) {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(55, 35, 12, 12);
        }
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception {
        return sha256Service.calculate(new ByteArrayInputStream(bytes)).hash();
    }

    private void assertTempDirectoryEmpty() throws Exception {
        try (var files = Files.list(testDirectory.resolve("uploads/temp"))) {
            assertThat(files).isEmpty();
        }
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(41L);
        user.setUuid("99999999-9999-9999-9999-999999999999");
        user.setFullName("Originality Checker");
        user.setUsername("originality-checker");
        user.setEmail("checker-private@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }

    private record ConfirmedSetup(
            RegisteredOriginalCandidateService.Candidate candidate,
            BlockchainOriginalResponse ledgerOriginal) {
    }
}
