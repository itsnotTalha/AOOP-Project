package com.authvault.service.impl;

import com.authvault.dto.asset.KnownOriginalComparisonResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResult;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.dto.asset.SimilarImageMatchResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.verification.VerificationEvidenceResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationEvidence;
import com.authvault.exception.BlockchainException;
import com.authvault.repository.AuthenticatorReviewRepository;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationEvidenceRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.BlockchainRegistryService;
import com.authvault.service.KnownOriginalCandidateService;
import com.authvault.service.KnownOriginalComparisonService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEvidenceServiceImplTest {

    private static final String SHA256 = "a".repeat(64);

    @Mock
    private DigitalAssetRepository assetRepository;
    @Mock
    private VerificationEvidenceRepository evidenceRepository;
    @Mock
    private AuthenticatorReviewRepository reviewRepository;
    @Mock
    private KnownOriginalCandidateService candidateService;
    @Mock
    private KnownOriginalComparisonService comparisonService;
    @Mock
    private BlockchainRegistryService blockchainRegistryService;

    private User owner;
    private VerificationEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        owner = user(User.Role.USER);
        CustomUserDetails details = new CustomUserDetails(owner);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()));
        service = new VerificationEvidenceServiceImpl(
                assetRepository,
                evidenceRepository,
                reviewRepository,
                candidateService,
                comparisonService,
                blockchainRegistryService,
                new Sha256ServiceImpl(),
                new VerificationEvidenceResponseMapper());
        when(evidenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void imageEvidenceRetainsShaPhashCandidateComparisonAndFabricResult() {
        DigitalAsset asset = asset(DigitalAsset.AssetType.IMAGE);
        String candidateId = UUID.randomUUID().toString();
        prepareNewEvidence(asset);
        when(candidateService.findSimilarImages(asset.getUuid())).thenReturn(
                SimilarImagesResponse.builder()
                        .assetId(asset.getUuid())
                        .perceptualHash(asset.getPerceptualHash())
                        .closestMatches(List.of(SimilarImageMatchResponse.builder()
                                .matchedAssetId(candidateId)
                                .hammingDistance(3)
                                .matchBand(PerceptualMatchBand.NEAR_DUPLICATE)
                                .build()))
                        .build());
        when(comparisonService.compareWithKnownOriginal(asset.getUuid())).thenReturn(
                KnownOriginalComparisonResponse.builder()
                        .assetId(asset.getUuid())
                        .candidateFound(true)
                        .comparisonPerformed(true)
                        .comparison(KnownOriginalComparisonResult.builder()
                                .status("COMPLETED")
                                .differencePerformed(true)
                                .changedAreaRatio(0.12)
                                .meanAbsoluteDifference(8.5)
                                .structuralSimilarity(0.91)
                                .build())
                        .build());
        BlockchainOriginalResponse original = new BlockchainOriginalResponse(
                candidateId, "b".repeat(64), "b".repeat(64), SHA256,
                "IMAGE", "VERIFIED", "c".repeat(64), Instant.now());
        when(blockchainRegistryService.findBySha256(SHA256))
                .thenReturn(new BlockchainOriginalLookupResponse(true, original));

        VerificationEvidenceResponse response = service.generateEvidence(asset.getUuid());

        assertThat(response.sha256()).isEqualTo(SHA256);
        assertThat(response.perceptualHash()).isEqualTo(asset.getPerceptualHash());
        assertThat(response.similarCandidateCount()).isEqualTo(1);
        assertThat(response.bestCandidateAssetId()).isEqualTo(candidateId);
        assertThat(response.bestPhashDistance()).isEqualTo(3);
        assertThat(response.comparisonPerformed()).isTrue();
        assertThat(response.comparisonStatus()).isEqualTo("COMPLETED");
        assertThat(response.changedAreaRatio()).isEqualTo(0.12);
        assertThat(response.fabricLookupPerformed()).isTrue();
        assertThat(response.fabricRegisteredOriginalFound()).isTrue();
        assertThat(response.fabricReferenceAssetId()).isEqualTo(candidateId);
        assertThat(response.evidenceHash()).matches("^[0-9a-f]{64}$");
        ArgumentCaptor<VerificationEvidence> evidenceCaptor =
                ArgumentCaptor.forClass(VerificationEvidence.class);
        verify(evidenceRepository).save(evidenceCaptor.capture());
        assertThat(service.hashEvidence(evidenceCaptor.getValue()))
                .isEqualTo(response.evidenceHash());
        assertThat(asset.getVerificationStatus())
                .isEqualTo(DigitalAsset.VerificationStatus.PENDING_REVIEW);
    }

    @Test
    void unavailableComparisonAndFabricAreRecordedWithoutRejectingAsset() {
        DigitalAsset asset = asset(DigitalAsset.AssetType.IMAGE);
        prepareNewEvidence(asset);
        when(candidateService.findSimilarImages(asset.getUuid())).thenReturn(
                SimilarImagesResponse.builder()
                        .assetId(asset.getUuid())
                        .perceptualHash(asset.getPerceptualHash())
                        .closestMatches(List.of(SimilarImageMatchResponse.builder()
                                .matchedAssetId(UUID.randomUUID().toString())
                                .hammingDistance(4)
                                .matchBand(PerceptualMatchBand.NEAR_DUPLICATE)
                                .build()))
                        .build());
        when(comparisonService.compareWithKnownOriginal(asset.getUuid()))
                .thenThrow(new RuntimeException("comparison offline"));
        when(blockchainRegistryService.findBySha256(SHA256)).thenThrow(
                new BlockchainException(
                        "BLOCKCHAIN_UNAVAILABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "offline"));

        VerificationEvidenceResponse response = service.generateEvidence(asset.getUuid());

        assertThat(response.comparisonPerformed()).isFalse();
        assertThat(response.comparisonReason()).isEqualTo("COMPARISON_UNAVAILABLE");
        assertThat(response.fabricLookupPerformed()).isFalse();
        assertThat(response.fabricStatus()).isEqualTo("BLOCKCHAIN_UNAVAILABLE");
        assertThat(response.fabricRegisteredOriginalFound()).isNull();
        assertThat(asset.getVerificationStatus())
                .isEqualTo(DigitalAsset.VerificationStatus.PENDING_REVIEW);
    }

    @Test
    void documentEvidenceDoesNotInvokePhashOrImageComparison() {
        DigitalAsset asset = asset(DigitalAsset.AssetType.DOCUMENT);
        asset.setPerceptualHash(null);
        prepareNewEvidence(asset);
        when(blockchainRegistryService.findBySha256(SHA256))
                .thenReturn(new BlockchainOriginalLookupResponse(false, null));

        VerificationEvidenceResponse response = service.generateEvidence(asset.getUuid());

        assertThat(response.perceptualHash()).isNull();
        assertThat(response.perceptualHashStatus()).isEqualTo("NOT_APPLICABLE_DOCUMENT");
        assertThat(response.similarCandidateCount()).isNull();
        assertThat(response.comparisonPerformed()).isFalse();
        assertThat(response.comparisonReason()).isEqualTo("NOT_APPLICABLE_DOCUMENT");
        verify(candidateService, never()).findSimilarImages(any());
        verify(comparisonService, never()).compareWithKnownOriginal(any());
    }

    private void prepareNewEvidence(DigitalAsset asset) {
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), owner))
                .thenReturn(Optional.of(asset));
        when(reviewRepository.existsByEvidence_Asset(asset)).thenReturn(false);
        when(evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset))
                .thenReturn(Optional.empty());
        when(assetRepository.existsBySha256HashAndUuidNot(SHA256, asset.getUuid()))
                .thenReturn(false);
    }

    private DigitalAsset asset(DigitalAsset.AssetType type) {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setOwner(owner);
        asset.setCurrentOwner(owner);
        asset.setTitle("Review asset");
        asset.setAssetType(type);
        asset.setSha256Hash(SHA256);
        asset.setPerceptualHash(type == DigitalAsset.AssetType.IMAGE
                ? "0123456789abcdef" : null);
        asset.setUploadDate(LocalDateTime.now());
        asset.setVerificationStatus(DigitalAsset.VerificationStatus.VERIFIED);
        return asset;
    }

    private User user(User.Role role) {
        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername("owner");
        user.setPasswordHash("encoded");
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
