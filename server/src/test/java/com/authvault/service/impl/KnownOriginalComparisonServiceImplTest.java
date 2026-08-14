package com.authvault.service.impl;

import com.authvault.client.ai.AiClientResult;
import com.authvault.client.ai.AiForensicsClient;
import com.authvault.dto.ai.AiImageComparisonResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResponse;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.asset.SimilarImageMatchResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.AssetStorageService;
import com.authvault.service.KnownOriginalCandidateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnownOriginalComparisonServiceImplTest {

    private DigitalAssetRepository repository;
    private KnownOriginalCandidateService candidateService;
    private AssetStorageService storageService;
    private AiForensicsClient aiClient;
    private KnownOriginalComparisonServiceImpl service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        repository = mock(DigitalAssetRepository.class);
        candidateService = mock(KnownOriginalCandidateService.class);
        storageService = mock(AssetStorageService.class);
        aiClient = mock(AiForensicsClient.class);
        service = new KnownOriginalComparisonServiceImpl(
                repository, candidateService, storageService, aiClient);

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
    void ownerComparisonSelectsClosestPlausibleCandidateAndSendsCorrectImages() throws Exception {
        DigitalAsset target = image("target", "images/target.png", "image/png");
        DigitalAsset candidate = image("candidate", "images/candidate.jpg", "image/jpeg");
        SimilarImageMatchResponse selected = match(
                candidate, 4, PerceptualMatchBand.NEAR_DUPLICATE);
        SimilarImageMatchResponse noMatch = match(
                image("unrelated", "images/unrelated.png", "image/png"),
                24,
                PerceptualMatchBand.NO_MATCH);
        byte[] referenceBytes = "controlled-reference".getBytes();
        byte[] targetBytes = "controlled-target".getBytes();
        AtomicReference<byte[]> sentReference = new AtomicReference<>();
        AtomicReference<byte[]> sentTarget = new AtomicReference<>();

        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findByUuidAndCurrentOwner(candidate.getUuid(), currentUser))
                .thenReturn(Optional.of(candidate));
        when(candidateService.findSimilarImages(target.getUuid()))
                .thenReturn(search(target, List.of(selected, noMatch)));
        when(storageService.loadStoredAsset(candidate.getStoragePath()))
                .thenReturn(new ByteArrayInputStream(referenceBytes));
        when(storageService.loadStoredAsset(target.getStoragePath()))
                .thenReturn(new ByteArrayInputStream(targetBytes));
        when(aiClient.compareImages(any(), any(), any(), any())).thenAnswer(invocation -> {
            sentReference.set(((Resource) invocation.getArgument(0)).getInputStream().readAllBytes());
            sentTarget.set(((Resource) invocation.getArgument(2)).getInputStream().readAllBytes());
            assertEquals(MediaType.IMAGE_JPEG, invocation.getArgument(1));
            assertEquals(MediaType.IMAGE_PNG, invocation.getArgument(3));
            return AiClientResult.success(internalComparison());
        });

        KnownOriginalComparisonResponse response =
                service.compareWithKnownOriginal(target.getUuid());

        assertTrue(response.isCandidateFound());
        assertTrue(response.isComparisonPerformed());
        assertEquals(candidate.getUuid(), response.getCandidate().getAssetId());
        assertEquals(4, response.getCandidate().getHammingDistance());
        assertEquals("COMPLETED", response.getComparison().getStatus());
        assertEquals("ORB_HOMOGRAPHY", response.getComparison().getAlignment().getMethod());
        assertEquals(0.083, response.getComparison().getChangedAreaRatio());
        assertEquals("bWFzaw==", response.getComparison().getChangeMaskPngBase64());
        assertNull(response.getReason());
        assertArrayEquals(referenceBytes, sentReference.get());
        assertArrayEquals(targetBytes, sentTarget.get());
    }

    @Test
    void noPreviousOrOnlyNoMatchReturnsNoKnownOriginalWithoutCallingAiService() {
        DigitalAsset target = image("target", "images/target.png", "image/png");
        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(candidateService.findSimilarImages(target.getUuid()))
                .thenReturn(search(target, List.of(match(
                        image("other", "images/other.png", "image/png"),
                        30,
                        PerceptualMatchBand.NO_MATCH))));

        KnownOriginalComparisonResponse response =
                service.compareWithKnownOriginal(target.getUuid());

        assertFalse(response.isCandidateFound());
        assertFalse(response.isComparisonPerformed());
        assertEquals("NO_KNOWN_ORIGINAL", response.getReason());
        assertNull(response.getCandidate());
        assertNull(response.getComparison());
        verify(aiClient, never()).compareImages(any(), any(), any(), any());
        verify(storageService, never()).loadStoredAsset(any());
    }

    @Test
    void candidateNotOwnedByCurrentUserIsNotLoadedOrRevealed() {
        DigitalAsset target = image("target", "images/target.png", "image/png");
        DigitalAsset inaccessible = image("private", "images/private.png", "image/png");
        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findByUuidAndCurrentOwner(inaccessible.getUuid(), currentUser))
                .thenReturn(Optional.empty());
        when(candidateService.findSimilarImages(target.getUuid()))
                .thenReturn(search(target, List.of(match(
                        inaccessible, 2, PerceptualMatchBand.NEAR_DUPLICATE))));

        KnownOriginalComparisonResponse response =
                service.compareWithKnownOriginal(target.getUuid());

        assertFalse(response.isCandidateFound());
        assertEquals("NO_KNOWN_ORIGINAL", response.getReason());
        verify(storageService, never()).loadStoredAsset(any());
    }

    @Test
    void exactVisualHashStillRunsComparison() {
        DigitalAsset target = image("target", "images/target.png", "image/png");
        DigitalAsset candidate = image("candidate", "images/candidate.png", "image/png");
        configureCandidate(target, candidate, PerceptualMatchBand.EXACT_VISUAL_HASH);
        when(aiClient.compareImages(any(), any(), any(), any()))
                .thenReturn(AiClientResult.success(internalComparison()));

        KnownOriginalComparisonResponse response =
                service.compareWithKnownOriginal(target.getUuid());

        assertTrue(response.isComparisonPerformed());
        verify(aiClient).compareImages(any(), any(), any(), any());
    }

    @Test
    void disabledOrUnavailableAiServiceProducesControlledCondition() {
        DigitalAsset target = image("target", "images/target.png", "image/png");
        DigitalAsset candidate = image("candidate", "images/candidate.png", "image/png");
        configureCandidate(target, candidate, PerceptualMatchBand.POSSIBLE_MATCH);
        when(aiClient.compareImages(any(), any(), any(), any()))
                .thenReturn(AiClientResult.unavailable());

        KnownOriginalComparisonResponse unavailable =
                service.compareWithKnownOriginal(target.getUuid());

        assertTrue(unavailable.isCandidateFound());
        assertFalse(unavailable.isComparisonPerformed());
        assertEquals("AI_SERVICE_UNAVAILABLE", unavailable.getReason());

        configureCandidate(target, candidate, PerceptualMatchBand.POSSIBLE_MATCH);
        when(aiClient.compareImages(any(), any(), any(), any()))
                .thenReturn(AiClientResult.disabled());
        KnownOriginalComparisonResponse disabled =
                service.compareWithKnownOriginal(target.getUuid());
        assertEquals("AI_SERVICE_DISABLED", disabled.getReason());
    }

    @Test
    void rejectsDocumentAndHidesNonexistentOrNonOwnedTarget() {
        DigitalAsset document = image("document", "documents/document.pdf", "application/pdf");
        document.setAssetType(DigitalAsset.AssetType.DOCUMENT);
        when(repository.findByUuidAndCurrentOwner(document.getUuid(), currentUser))
                .thenReturn(Optional.of(document));

        assertThrows(BadRequestException.class,
                () -> service.compareWithKnownOriginal(document.getUuid()));

        String missingId = UUID.randomUUID().toString();
        when(repository.findByUuidAndCurrentOwner(missingId, currentUser))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.compareWithKnownOriginal(missingId));
    }

    private void configureCandidate(
            DigitalAsset target,
            DigitalAsset candidate,
            PerceptualMatchBand matchBand) {
        when(repository.findByUuidAndCurrentOwner(target.getUuid(), currentUser))
                .thenReturn(Optional.of(target));
        when(repository.findByUuidAndCurrentOwner(candidate.getUuid(), currentUser))
                .thenReturn(Optional.of(candidate));
        when(candidateService.findSimilarImages(target.getUuid()))
                .thenReturn(search(target, List.of(match(candidate, 0, matchBand))));
        when(storageService.loadStoredAsset(candidate.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("reference".getBytes()));
        when(storageService.loadStoredAsset(target.getStoragePath()))
                .thenReturn(new ByteArrayInputStream("target".getBytes()));
    }

    private SimilarImagesResponse search(
            DigitalAsset target,
            List<SimilarImageMatchResponse> matches) {
        return SimilarImagesResponse.builder()
                .assetId(target.getUuid())
                .perceptualHash("0000000000000000")
                .closestMatches(matches)
                .build();
    }

    private SimilarImageMatchResponse match(
            DigitalAsset candidate,
            int distance,
            PerceptualMatchBand matchBand) {
        return SimilarImageMatchResponse.builder()
                .matchedAssetId(candidate.getUuid())
                .title(candidate.getTitle())
                .originalFilename(candidate.getOriginalFilename())
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .hammingDistance(distance)
                .matchBand(matchBand)
                .build();
    }

    private DigitalAsset image(String uuid, String storagePath, String mimeType) {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(uuid);
        asset.setCurrentOwner(currentUser);
        asset.setAssetType(DigitalAsset.AssetType.IMAGE);
        asset.setTitle("Title " + uuid);
        asset.setOriginalFilename(uuid + ".png");
        asset.setStoragePath(storagePath);
        asset.setMimeType(mimeType);
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

    private AiImageComparisonResponse internalComparison() {
        return new AiImageComparisonResponse(
                "1",
                new AiImageComparisonResponse.Alignment(
                        "ALIGNED", "ORB_HOMOGRAPHY", 80, 75, 42, 31, 0.738),
                new AiImageComparisonResponse.Difference(
                        true, 0.083, 12.6, null),
                new AiImageComparisonResponse.ChangeMask(
                        true, "png", "bWFzaw=="),
                "COMPLETED");
    }
}
