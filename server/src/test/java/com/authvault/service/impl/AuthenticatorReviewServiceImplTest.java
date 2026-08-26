package com.authvault.service.impl;

import com.authvault.dto.verification.AuthenticatorReviewResponse;
import com.authvault.entity.AuthenticatorReview;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationEvidence;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.DuplicateResourceException;
import com.authvault.exception.ForbiddenException;
import com.authvault.repository.AuthenticatorReviewRepository;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationEvidenceRepository;
import com.authvault.security.user.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatorReviewServiceImplTest {

    @Mock
    private DigitalAssetRepository assetRepository;
    @Mock
    private VerificationEvidenceRepository evidenceRepository;
    @Mock
    private AuthenticatorReviewRepository reviewRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryUserCannotApproveOrReject() {
        authenticate(User.Role.USER);
        AuthenticatorReviewServiceImpl service = service();

        assertThatThrownBy(() -> service.approve(UUID.randomUUID().toString(), null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reject(UUID.randomUUID().toString(), "reason"))
                .isInstanceOf(ForbiddenException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void authenticatorApprovalPersistsReviewerTimestampAndFinalStatus() {
        User reviewer = authenticate(User.Role.AUTHENTICATOR);
        DigitalAsset asset = pendingAsset();
        VerificationEvidence evidence = evidence(asset);
        preparePending(asset, evidence);
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatorReviewResponse response = service().approve(asset.getUuid(), "Reviewed");

        assertThat(response.decision()).isEqualTo("VERIFIED");
        assertThat(response.reviewerUserId()).isEqualTo(reviewer.getUuid());
        assertThat(response.evidenceId()).isEqualTo(evidence.getUuid());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(asset.getVerificationStatus())
                .isEqualTo(DigitalAsset.VerificationStatus.VERIFIED);
        verify(reviewRepository).save(any(AuthenticatorReview.class));
    }

    @Test
    void adminMayRejectButRejectionRequiresReason() {
        authenticate(User.Role.ADMIN);
        DigitalAsset asset = pendingAsset();
        VerificationEvidence evidence = evidence(asset);
        preparePending(asset, evidence);

        assertThatThrownBy(() -> service().reject(asset.getUuid(), "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A rejection reason is required");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void authenticatorCanRejectPendingAssetWithReason() {
        User reviewer = authenticate(User.Role.AUTHENTICATOR);
        DigitalAsset asset = pendingAsset();
        VerificationEvidence evidence = evidence(asset);
        preparePending(asset, evidence);
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatorReviewResponse response = service().reject(
                asset.getUuid(), "Visual differences require rejection");

        assertThat(response.decision()).isEqualTo("REJECTED");
        assertThat(response.reason()).isEqualTo("Visual differences require rejection");
        assertThat(response.reviewerUserId()).isEqualTo(reviewer.getUuid());
        assertThat(response.evidenceId()).isEqualTo(evidence.getUuid());
        assertThat(asset.getVerificationStatus())
                .isEqualTo(DigitalAsset.VerificationStatus.REJECTED);
    }

    @Test
    void finalizedReviewCannotBeSilentlyOverwritten() {
        authenticate(User.Role.AUTHENTICATOR);
        DigitalAsset asset = pendingAsset();
        VerificationEvidence evidence = evidence(asset);
        when(assetRepository.findByUuid(asset.getUuid())).thenReturn(Optional.of(asset));
        when(evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset))
                .thenReturn(Optional.of(evidence));
        when(reviewRepository.findByEvidence(evidence))
                .thenReturn(Optional.of(new AuthenticatorReview()));

        assertThatThrownBy(() -> service().approve(asset.getUuid(), null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Verification has already been finalized");
        verify(reviewRepository, never()).save(any());
    }

    private AuthenticatorReviewServiceImpl service() {
        return new AuthenticatorReviewServiceImpl(
                assetRepository,
                evidenceRepository,
                reviewRepository,
                new VerificationEvidenceResponseMapper());
    }

    private void preparePending(DigitalAsset asset, VerificationEvidence evidence) {
        when(assetRepository.findByUuid(asset.getUuid())).thenReturn(Optional.of(asset));
        when(evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset))
                .thenReturn(Optional.of(evidence));
        when(reviewRepository.findByEvidence(evidence)).thenReturn(Optional.empty());
    }

    private User authenticate(User.Role role) {
        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(role.name().toLowerCase());
        user.setPasswordHash("encoded");
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()));
        return user;
    }

    private DigitalAsset pendingAsset() {
        DigitalAsset asset = new DigitalAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setTitle("Pending asset");
        asset.setAssetType(DigitalAsset.AssetType.IMAGE);
        asset.setVerificationStatus(DigitalAsset.VerificationStatus.PENDING_REVIEW);
        asset.setUploadDate(LocalDateTime.now());
        return asset;
    }

    private VerificationEvidence evidence(DigitalAsset asset) {
        VerificationEvidence evidence = new VerificationEvidence();
        evidence.setUuid(UUID.randomUUID().toString());
        evidence.setAsset(asset);
        evidence.setGeneratedAt(LocalDateTime.now());
        return evidence;
    }
}
