package com.authvault.service.impl;

import com.authvault.config.PerceptualHashProperties;
import com.authvault.dto.asset.PerceptualMatchBand;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.RegisteredOriginalCandidateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredOriginalCandidateServiceImplTest {

    private DigitalAssetRepository repository;
    private PerceptualHashProperties properties;
    private RegisteredOriginalCandidateServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(DigitalAssetRepository.class);
        properties = new PerceptualHashProperties();
        service = new RegisteredOriginalCandidateServiceImpl(
                repository, new PerceptualHashServiceImpl(), properties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesAcrossOwnersRanksPlausibleVerifiedImagesAndBoundsDatabaseScan() {
        User creatorA = user("creator-a");
        User checkerB = user("checker-b");
        CustomUserDetails checkerDetails = new CustomUserDetails(checkerB);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        checkerDetails, null, checkerDetails.getAuthorities()));
        DigitalAsset creatorOriginal = image(
                creatorA, "0000000000000001", "a", DigitalAsset.VerificationStatus.VERIFIED);
        DigitalAsset checkerImage = image(
                checkerB, "0000000000000003", "b", DigitalAsset.VerificationStatus.VERIFIED);
        DigitalAsset unrelated = image(
                creatorA, "ffffffffffffffff", "c", DigitalAsset.VerificationStatus.VERIFIED);
        DigitalAsset rejected = image(
                creatorA, "0000000000000000", "d", DigitalAsset.VerificationStatus.REJECTED);
        properties.setRegistryScanLimit(25);
        properties.setMaxCandidates(2);
        when(repository.findVerifiedImageRegistryCandidates(any()))
                .thenReturn(List.of(unrelated, checkerImage, rejected, creatorOriginal));

        List<RegisteredOriginalCandidateService.Candidate> candidates =
                service.findPlausibleCandidates("0000000000000000");

        assertThat(candidates).extracting(RegisteredOriginalCandidateService.Candidate::assetId)
                .containsExactly(creatorOriginal.getUuid(), checkerImage.getUuid());
        assertThat(candidates.getFirst().pHashDistance()).isEqualTo(1);
        assertThat(candidates.getFirst().matchBand())
                .isEqualTo(PerceptualMatchBand.NEAR_DUPLICATE);
        assertThat(candidates).noneMatch(candidate ->
                candidate.assetId().equals(rejected.getUuid())
                        || candidate.assetId().equals(unrelated.getUuid()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findVerifiedImageRegistryCandidates(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void excludesInvalidHashesAndNoMatchBand() {
        DigitalAsset invalidPHash = image(
                user("creator"), "invalid", "a", DigitalAsset.VerificationStatus.VERIFIED);
        DigitalAsset invalidSha = image(
                user("creator"), "0000000000000001", "b", DigitalAsset.VerificationStatus.VERIFIED);
        invalidSha.setSha256Hash("invalid");
        DigitalAsset noMatch = image(
                user("creator"), "ffffffffffffffff", "c", DigitalAsset.VerificationStatus.VERIFIED);
        when(repository.findVerifiedImageRegistryCandidates(any()))
                .thenReturn(List.of(invalidPHash, invalidSha, noMatch));

        assertThat(service.findPlausibleCandidates("0000000000000000")).isEmpty();
    }

    private DigitalAsset image(
            User owner,
            String pHash,
            String suffix,
            DigitalAsset.VerificationStatus status) {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.nameUUIDFromBytes((owner.getUsername() + suffix).getBytes()).toString());
        asset.setOwner(owner);
        asset.setCurrentOwner(owner);
        asset.setAssetType(DigitalAsset.AssetType.IMAGE);
        asset.setVerificationStatus(status);
        asset.setPerceptualHash(pHash);
        asset.setSha256Hash("a".repeat(63) + suffix);
        asset.setStoragePath("images/" + asset.getUuid() + ".png");
        asset.setUploadDate(LocalDateTime.now());
        return asset;
    }

    private User user(String username) {
        User user = new User();
        user.setUuid(UUID.nameUUIDFromBytes(username.getBytes()).toString());
        user.setUsername(username);
        return user;
    }
}
