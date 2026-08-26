package com.authvault.service.impl;

import com.authvault.blockchain.CanonicalSha256Hasher;
import com.authvault.blockchain.CreatorIdHasher;
import com.authvault.blockchain.OriginalRegistryClient;
import com.authvault.blockchain.OriginalRegistryClientException;
import com.authvault.blockchain.RegistrationEvidenceHasher;
import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationHistory;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.BlockchainException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationHistoryRepository;
import com.authvault.security.user.CustomUserDetails;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainRegistryServiceImplTest {

    private static final String SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final LocalDateTime VERIFIED_AT =
            LocalDateTime.of(2026, 8, 15, 11, 22, 33, 123_000_000);

    @Mock
    private DigitalAssetRepository assetRepository;
    @Mock
    private VerificationHistoryRepository historyRepository;
    @Mock
    private OriginalRegistryClient registryClient;

    private User currentUser;
    private BlockchainRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        currentUser = authenticatedUser();
        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));

        CanonicalSha256Hasher canonicalHasher =
                new CanonicalSha256Hasher(new Sha256ServiceImpl());
        service = new BlockchainRegistryServiceImpl(
                assetRepository,
                historyRepository,
                registryClient,
                new CreatorIdHasher(canonicalHasher),
                new RegistrationEvidenceHasher(canonicalHasher));
        lenient().when(registryClient.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanRegisterVerifiedImageUsingTrustedCanonicalValues() {
        DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.IMAGE);
        VerificationHistory history = verificationHistory(asset);
        BlockchainOriginalResponse original = original(asset);
        BlockchainRegistrationResponse expected =
                new BlockchainRegistrationResponse("tx-123", original);
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(historyRepository.findByAssetOrderByVerifiedAtDesc(asset))
                .thenReturn(List.of(history));
        when(registryClient.registerOriginal(any())).thenReturn(expected);

        assertThat(service.registerOwnedAsset(asset.getUuid())).isEqualTo(expected);

        ArgumentCaptor<OriginalRegistryClient.RegistrationRequest> captor =
                ArgumentCaptor.forClass(OriginalRegistryClient.RegistrationRequest.class);
        verify(registryClient).registerOriginal(captor.capture());
        OriginalRegistryClient.RegistrationRequest request = captor.getValue();
        assertThat(request.assetId()).isEqualTo(asset.getUuid());
        assertThat(request.creatorIdHash()).matches("^[0-9a-f]{64}$");
        assertThat(request.creatorIdHash()).doesNotContain(currentUser.getUuid());
        assertThat(request.sha256()).isEqualTo(SHA256);
        assertThat(request.assetType()).isEqualTo("IMAGE");
        assertThat(request.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(request.evidenceHash()).matches("^[0-9a-f]{64}$");

        String firstEvidenceHash = request.evidenceHash();
        when(registryClient.registerOriginal(any())).thenReturn(expected);
        service.registerOwnedAsset(asset.getUuid());
        verify(registryClient, org.mockito.Mockito.times(2)).registerOriginal(captor.capture());
        assertThat(captor.getValue().evidenceHash()).isEqualTo(firstEvidenceHash);
    }

    @Test
    void verifiedDocumentIsSupported() {
        DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.DOCUMENT);
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(historyRepository.findByAssetOrderByVerifiedAtDesc(asset))
                .thenReturn(List.of(verificationHistory(asset)));
        when(registryClient.registerOriginal(any())).thenReturn(
                new BlockchainRegistrationResponse("tx-doc", original(asset)));

        service.registerOwnedAsset(asset.getUuid());

        ArgumentCaptor<OriginalRegistryClient.RegistrationRequest> captor =
                ArgumentCaptor.forClass(OriginalRegistryClient.RegistrationRequest.class);
        verify(registryClient).registerOriginal(captor.capture());
        assertThat(captor.getValue().assetType()).isEqualTo("DOCUMENT");
    }

    @Test
    void pendingAndRejectedAssetsAreRejectedWithoutFabricInvocation() {
        for (DigitalAsset.VerificationStatus status : List.of(
                DigitalAsset.VerificationStatus.PENDING,
                DigitalAsset.VerificationStatus.REJECTED)) {
            DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.IMAGE);
            asset.setVerificationStatus(status);
            when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                    .thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.registerOwnedAsset(asset.getUuid()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Only VERIFIED assets can be registered on the blockchain");
        }
        verify(registryClient, never()).registerOriginal(any());
    }

    @Test
    void assetNotOwnedByCurrentUserIsHiddenAsNotFound() {
        String assetId = UUID.randomUUID().toString();
        when(assetRepository.findByUuidAndCurrentOwner(assetId, currentUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerOwnedAsset(assetId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Asset not found");
        verify(registryClient, never()).registerOriginal(any());
    }

    @Test
    void mapsDuplicateAndNetworkFailuresToStableApplicationErrors() {
        DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.IMAGE);
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(historyRepository.findByAssetOrderByVerifiedAtDesc(asset))
                .thenReturn(List.of(verificationHistory(asset)));

        assertMappedRegistrationFailure(
                OriginalRegistryClientException.Reason.DUPLICATE_ASSET,
                "BLOCKCHAIN_DUPLICATE_ASSET", HttpStatus.CONFLICT, asset);
        assertMappedRegistrationFailure(
                OriginalRegistryClientException.Reason.DUPLICATE_SHA256,
                "BLOCKCHAIN_DUPLICATE_SHA256", HttpStatus.CONFLICT, asset);
        assertMappedRegistrationFailure(
                OriginalRegistryClientException.Reason.UNAVAILABLE,
                "BLOCKCHAIN_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, asset);
    }

    @Test
    void getsOwnedRegistrationAndMapsMissingLedgerAsset() {
        DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.IMAGE);
        BlockchainOriginalResponse expected = original(asset);
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(registryClient.getOriginal(asset.getUuid())).thenReturn(expected);

        assertThat(service.getOwnedAssetRegistration(asset.getUuid())).isEqualTo(expected);

        when(registryClient.getOriginal(asset.getUuid())).thenThrow(
                clientFailure(OriginalRegistryClientException.Reason.ASSET_NOT_FOUND));
        assertThatThrownBy(() -> service.getOwnedAssetRegistration(asset.getUuid()))
                .isInstanceOfSatisfying(BlockchainException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BLOCKCHAIN_ASSET_NOT_FOUND");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void rejectsMalformedShaBeforeCallingFabric() {
        assertThatThrownBy(() -> service.findBySha256("ABC"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("SHA-256 must be exactly 64 lowercase hexadecimal characters");
        verify(registryClient, never()).findBySha256(any());
    }

    @Test
    void exactShaLookupRepresentsFoundAndMissingResults() {
        BlockchainOriginalResponse original = original(eligibleAsset(DigitalAsset.AssetType.IMAGE));
        when(registryClient.findBySha256(SHA256)).thenReturn(Optional.of(original));

        BlockchainOriginalLookupResponse found = service.findBySha256(SHA256);
        assertThat(found.found()).isTrue();
        assertThat(found.asset()).isEqualTo(original);

        when(registryClient.findBySha256(SHA256)).thenReturn(Optional.empty());
        BlockchainOriginalLookupResponse missing = service.findBySha256(SHA256);
        assertThat(missing.found()).isFalse();
        assertThat(missing.asset()).isNull();
    }

    @Test
    void returnsFabricHistoryForOwnedAsset() {
        DigitalAsset asset = eligibleAsset(DigitalAsset.AssetType.IMAGE);
        BlockchainHistoryResponse entry = new BlockchainHistoryResponse(
                "tx-history", Instant.parse("2026-08-15T05:22:33Z"), false, original(asset));
        when(assetRepository.findByUuidAndCurrentOwner(asset.getUuid(), currentUser))
                .thenReturn(Optional.of(asset));
        when(registryClient.getAssetHistory(asset.getUuid())).thenReturn(List.of(entry));

        assertThat(service.getOwnedAssetHistory(asset.getUuid())).containsExactly(entry);
    }

    @Test
    void disabledModeFailsWithControlledErrorBeforeRepositoryAccess() {
        when(registryClient.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.registerOwnedAsset(UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(BlockchainException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BLOCKCHAIN_DISABLED");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verify(assetRepository, never()).findByUuidAndCurrentOwner(any(), any());
    }

    private void assertMappedRegistrationFailure(
            OriginalRegistryClientException.Reason reason,
            String expectedCode,
            HttpStatus expectedStatus,
            DigitalAsset asset) {
        doThrow(clientFailure(reason)).when(registryClient).registerOriginal(any());
        assertThatThrownBy(() -> service.registerOwnedAsset(asset.getUuid()))
                .isInstanceOfSatisfying(BlockchainException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(expectedCode);
                    assertThat(exception.getStatus()).isEqualTo(expectedStatus);
                });
    }

    private OriginalRegistryClientException clientFailure(
            OriginalRegistryClientException.Reason reason) {
        return new OriginalRegistryClientException(reason, "test failure");
    }

    private DigitalAsset eligibleAsset(DigitalAsset.AssetType type) {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setOwner(currentUser);
        asset.setCurrentOwner(currentUser);
        asset.setAssetType(type);
        asset.setSha256Hash(SHA256);
        asset.setVerificationStatus(DigitalAsset.VerificationStatus.VERIFIED);
        return asset;
    }

    private VerificationHistory verificationHistory(DigitalAsset asset) {
        VerificationHistory history = new VerificationHistory();
        history.setAsset(asset);
        history.setVerifiedAt(VERIFIED_AT);
        history.setResult(VerificationHistory.Result.VERIFIED);
        return history;
    }

    private BlockchainOriginalResponse original(DigitalAsset asset) {
        return new BlockchainOriginalResponse(
                asset.getUuid(),
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                SHA256,
                asset.getAssetType().name(),
                "VERIFIED",
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                Instant.parse("2026-08-15T05:22:33Z"));
    }

    private User authenticatedUser() {
        User user = new User();
        user.setId(7L);
        user.setUuid("33333333-3333-3333-3333-333333333333");
        user.setUsername("blockchain-owner");
        user.setEmail("owner@example.com");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
