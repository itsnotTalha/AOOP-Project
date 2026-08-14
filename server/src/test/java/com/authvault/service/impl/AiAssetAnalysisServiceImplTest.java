package com.authvault.service.impl;

import com.authvault.client.ai.AiClientResult;
import com.authvault.client.ai.AiForensicsClient;
import com.authvault.dto.ai.AiImageAnalysisResponse;
import com.authvault.dto.asset.AssetAiAnalysisResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.AssetStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssetAnalysisServiceImplTest {

    private DigitalAssetRepository repository;
    private AssetStorageService storageService;
    private AiForensicsClient aiClient;
    private AiAssetAnalysisServiceImpl service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        repository = mock(DigitalAssetRepository.class);
        storageService = mock(AssetStorageService.class);
        aiClient = mock(AiForensicsClient.class);
        service = new AiAssetAnalysisServiceImpl(repository, storageService, aiClient);
        currentUser = user();
        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanAnalyzeImageAndCompletedUfdAndTruForFieldsAreMapped() throws Exception {
        DigitalAsset asset = imageAsset();
        byte[] controlledBytes = "controlled-image-bytes".getBytes();
        AtomicReference<byte[]> sentBytes = new AtomicReference<>();
        when(repository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(storageService.loadStoredAsset(asset.getStoragePath()))
                .thenReturn(new ByteArrayInputStream(controlledBytes));
        when(aiClient.analyzeImage(any(), eq(MediaType.IMAGE_PNG))).thenAnswer(invocation -> {
            Resource resource = invocation.getArgument(0);
            sentBytes.set(resource.getInputStream().readAllBytes());
            return AiClientResult.success(completedInternal(null));
        });

        AssetAiAnalysisResponse response = service.analyzeOwnedImage(asset.getUuid());

        var ufd = response.getAiGenerationAnalysis();
        assertTrue(ufd.isPerformed());
        assertEquals("COMPLETED", ufd.getStatus());
        assertEquals("UNIVERSAL_FAKE_DETECT", ufd.getModelName());
        assertEquals("UFD_CVPR2023_CLIP_VIT_L14_FC_V1", ufd.getModelVersion());
        assertEquals(0.6791787, ufd.getRawSyntheticScore());
        assertEquals(0.5, ufd.getDecisionThreshold());
        assertEquals("SYNTHETIC_LEANING", ufd.getModelSignal());
        assertEquals("NOT_CALIBRATED", ufd.getCalibrationStatus());
        assertNull(ufd.getCalibratedSyntheticProbability());
        var trufor = response.getManipulationAnalysis();
        assertTrue(trufor.isPerformed());
        assertEquals("COMPLETED", trufor.getStatus());
        assertEquals("TRUFOR", trufor.getModelName());
        assertEquals("TRUFOR_CVPR2023_RELEASED_V1", trufor.getModelVersion());
        assertEquals(0.82, trufor.getManipulationScore());
        assertEquals("ELEVATED_MANIPULATION_SIGNAL", trufor.getModelSignal());
        assertEquals(0.25, trufor.getSuspiciousAreaRatio());
        assertEquals(0.2, trufor.getReliableSuspiciousAreaRatio());
        assertEquals("YW5vbWFseQ==", trufor.getAnomalyMapPngBase64());
        assertEquals("cmVsaWFiaWxpdHk=", trufor.getReliabilityMapPngBase64());
        assertEquals("bWFzaw==", trufor.getSuspiciousMaskPngBase64());
        assertNotNull(response.getAnalyzedAt());
        assertArrayEquals(controlledBytes, sentBytes.get());
    }

    @Test
    void calibratedProbabilityIsMappedWithoutReplacingRawScore() {
        DigitalAsset asset = configuredImage();
        when(aiClient.analyzeImage(any(), any()))
                .thenReturn(AiClientResult.success(completedInternal(0.61)));

        AssetAiAnalysisResponse response = service.analyzeOwnedImage(asset.getUuid());

        assertEquals(0.6791787, response.getAiGenerationAnalysis().getRawSyntheticScore());
        assertEquals(0.61,
                response.getAiGenerationAnalysis().getCalibratedSyntheticProbability());
    }

    @Test
    void otherUserOrNonexistentAssetIsHiddenAsNotFound() {
        String inaccessibleId = UUID.randomUUID().toString();
        when(repository.findByUuidAndCurrentOwner(inaccessibleId, currentUser))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.analyzeOwnedImage(inaccessibleId));
        verify(storageService, never()).loadStoredAsset(any());
        verify(aiClient, never()).analyzeImage(any(), any());
    }

    @Test
    void documentIsRejectedWithoutLoadingOrCallingAiService() {
        DigitalAsset document = imageAsset();
        document.setAssetType(DigitalAsset.AssetType.DOCUMENT);
        when(repository.findByUuidAndCurrentOwner(document.getUuid(), currentUser))
                .thenReturn(Optional.of(document));

        assertThrows(BadRequestException.class,
                () -> service.analyzeOwnedImage(document.getUuid()));
        verify(storageService, never()).loadStoredAsset(any());
        verify(aiClient, never()).analyzeImage(any(), any());
    }

    @Test
    void disabledAndUnavailableAiServiceAreControlled() {
        DigitalAsset asset = configuredImage();
        when(aiClient.analyzeImage(any(), any())).thenReturn(AiClientResult.disabled());

        AssetAiAnalysisResponse disabled = service.analyzeOwnedImage(asset.getUuid());

        assertFalse(disabled.getAiGenerationAnalysis().isPerformed());
        assertFalse(disabled.getManipulationAnalysis().isPerformed());
        assertEquals("AI_SERVICE_DISABLED", disabled.getAiGenerationAnalysis().getStatus());
        assertEquals("AI_SERVICE_DISABLED", disabled.getManipulationAnalysis().getStatus());
        assertNull(disabled.getAiGenerationAnalysis().getRawSyntheticScore());

        when(storageService.loadStoredAsset(asset.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("image".getBytes()));
        when(aiClient.analyzeImage(any(), any())).thenReturn(AiClientResult.unavailable());
        AssetAiAnalysisResponse unavailable = service.analyzeOwnedImage(asset.getUuid());
        assertFalse(unavailable.getAiGenerationAnalysis().isPerformed());
        assertFalse(unavailable.getManipulationAnalysis().isPerformed());
        assertEquals("AI_SERVICE_UNAVAILABLE",
                unavailable.getAiGenerationAnalysis().getStatus());
        assertEquals("AI_SERVICE_UNAVAILABLE",
                unavailable.getManipulationAnalysis().getStatus());
    }

    @Test
    void independentDetectorStatesArePreserved() {
        DigitalAsset asset = configuredImage();
        AiImageAnalysisResponse ufdOnly = new AiImageAnalysisResponse(
                "1",
                completedUfd(null),
                unavailableTruFor("PROCESSING_TIMEOUT"));
        when(aiClient.analyzeImage(any(), any())).thenReturn(AiClientResult.success(ufdOnly));

        AssetAiAnalysisResponse first = service.analyzeOwnedImage(asset.getUuid());

        assertTrue(first.getAiGenerationAnalysis().isPerformed());
        assertFalse(first.getManipulationAnalysis().isPerformed());
        assertEquals("PROCESSING_TIMEOUT", first.getManipulationAnalysis().getStatus());

        when(storageService.loadStoredAsset(asset.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("image".getBytes()));
        AiImageAnalysisResponse truforOnly = new AiImageAnalysisResponse(
                "1",
                new AiImageAnalysisResponse.AiGenerationAnalysis(
                        false, null, null, null, null, null, null, null, null,
                        "MODEL_NOT_CONFIGURED"),
                completedTruFor());
        when(aiClient.analyzeImage(any(), any())).thenReturn(AiClientResult.success(truforOnly));

        AssetAiAnalysisResponse second = service.analyzeOwnedImage(asset.getUuid());

        assertFalse(second.getAiGenerationAnalysis().isPerformed());
        assertTrue(second.getManipulationAnalysis().isPerformed());
        assertEquals("bWFzaw==",
                second.getManipulationAnalysis().getSuspiciousMaskPngBase64());
    }

    private DigitalAsset configuredImage() {
        DigitalAsset asset = imageAsset();
        when(repository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(storageService.loadStoredAsset(asset.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("image".getBytes()));
        return asset;
    }

    private AiImageAnalysisResponse completedInternal(Double calibratedProbability) {
        return new AiImageAnalysisResponse(
                "1",
                completedUfd(calibratedProbability),
                completedTruFor());
    }

    private AiImageAnalysisResponse.AiGenerationAnalysis completedUfd(
            Double calibratedProbability) {
        return new AiImageAnalysisResponse.AiGenerationAnalysis(
                true,
                "UNIVERSAL_FAKE_DETECT",
                "UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
                0.75,
                0.6791787,
                0.5,
                "SYNTHETIC_LEANING",
                calibratedProbability == null ? "NOT_CALIBRATED" : "CALIBRATED",
                calibratedProbability,
                "COMPLETED");
    }

    private AiImageAnalysisResponse.ManipulationAnalysis completedTruFor() {
        return new AiImageAnalysisResponse.ManipulationAnalysis(
                true,
                "TRUFOR",
                "TRUFOR_CVPR2023_RELEASED_V1",
                0.82,
                0.5,
                "ELEVATED_MANIPULATION_SIGNAL",
                true,
                0.25,
                0.2,
                "YW5vbWFseQ==",
                "cmVsaWFiaWxpdHk=",
                "bWFzaw==",
                "COMPLETED");
    }

    private AiImageAnalysisResponse.ManipulationAnalysis unavailableTruFor(String status) {
        return new AiImageAnalysisResponse.ManipulationAnalysis(
                false, "TRUFOR", "TRUFOR_CVPR2023_RELEASED_V1",
                null, null, null, false, null, null, null, null, null, status);
    }

    private DigitalAsset imageAsset() {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setCurrentOwner(currentUser);
        asset.setAssetType(DigitalAsset.AssetType.IMAGE);
        asset.setStoragePath("images/server-generated.png");
        asset.setMimeType("image/png");
        return asset;
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setUuid(UUID.randomUUID().toString());
        user.setFullName("Owner");
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
