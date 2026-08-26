package com.authvault.service.impl;

import com.authvault.dto.verification.AuthenticatorReviewDetailResponse;
import com.authvault.dto.verification.AuthenticatorReviewResponse;
import com.authvault.dto.verification.PendingVerificationResponse;
import com.authvault.entity.AuthenticatorReview;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.entity.VerificationEvidence;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.DuplicateResourceException;
import com.authvault.exception.ForbiddenException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.AuthenticatorReviewRepository;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.VerificationEvidenceRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AuthenticatorReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('AUTHENTICATOR','ADMIN')")
public class AuthenticatorReviewServiceImpl implements AuthenticatorReviewService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final VerificationEvidenceRepository evidenceRepository;
    private final AuthenticatorReviewRepository reviewRepository;
    private final VerificationEvidenceResponseMapper evidenceMapper;

    public AuthenticatorReviewServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            VerificationEvidenceRepository evidenceRepository,
            AuthenticatorReviewRepository reviewRepository,
            VerificationEvidenceResponseMapper evidenceMapper) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.evidenceRepository = evidenceRepository;
        this.reviewRepository = reviewRepository;
        this.evidenceMapper = evidenceMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingVerificationResponse> getPendingReviews() {
        requireReviewer();
        return digitalAssetRepository.findByVerificationStatusOrderByUploadDateAsc(
                        DigitalAsset.VerificationStatus.PENDING_REVIEW).stream()
                .map(asset -> evidenceRepository
                        .findFirstByAssetOrderByGeneratedAtDesc(asset)
                        .map(evidence -> toPending(asset, evidence))
                        .orElse(null))
                .filter(item -> item != null)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatorReviewDetailResponse getReview(String assetId) {
        requireReviewer();
        DigitalAsset asset = findAsset(assetId);
        VerificationEvidence evidence = latestEvidence(asset);
        AuthenticatorReviewResponse review = reviewRepository.findByEvidence(evidence)
                .map(this::toReviewResponse)
                .orElse(null);
        return new AuthenticatorReviewDetailResponse(
                evidenceMapper.toResponse(evidence), review);
    }

    @Override
    @Transactional
    public AuthenticatorReviewResponse approve(String assetId, String reason) {
        return finalizeReview(assetId, AuthenticatorReview.Decision.VERIFIED, reason);
    }

    @Override
    @Transactional
    public AuthenticatorReviewResponse reject(String assetId, String reason) {
        return finalizeReview(assetId, AuthenticatorReview.Decision.REJECTED, reason);
    }

    private AuthenticatorReviewResponse finalizeReview(
            String assetId,
            AuthenticatorReview.Decision decision,
            String reason) {
        User reviewer = requireReviewer();
        DigitalAsset asset = findAsset(assetId);
        VerificationEvidence evidence = latestEvidence(asset);
        if (asset.getVerificationStatus() != DigitalAsset.VerificationStatus.PENDING_REVIEW
                || reviewRepository.findByEvidence(evidence).isPresent()) {
            throw new DuplicateResourceException("Verification has already been finalized");
        }

        String normalizedReason = normalizeReason(reason);
        if (decision == AuthenticatorReview.Decision.REJECTED
                && normalizedReason == null) {
            throw new BadRequestException("A rejection reason is required");
        }
        if (normalizedReason != null && normalizedReason.length() > 2000) {
            throw new BadRequestException("Review reason must not exceed 2000 characters");
        }

        AuthenticatorReview review = new AuthenticatorReview();
        review.setUuid(UUID.randomUUID().toString());
        review.setEvidence(evidence);
        review.setReviewer(reviewer);
        review.setDecision(decision);
        review.setReason(normalizedReason);
        review.setReviewedAt(LocalDateTime.now());

        asset.setVerificationStatus(decision == AuthenticatorReview.Decision.VERIFIED
                ? DigitalAsset.VerificationStatus.VERIFIED
                : DigitalAsset.VerificationStatus.REJECTED);
        digitalAssetRepository.save(asset);
        AuthenticatorReview saved = reviewRepository.save(review);
        return toReviewResponse(saved);
    }

    private User requireReviewer() {
        User reviewer = SecurityUtils.getCurrentUser();
        if (reviewer.getRole() != User.Role.AUTHENTICATOR
                && reviewer.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Authenticator role is required");
        }
        return reviewer;
    }

    private DigitalAsset findAsset(String assetId) {
        return digitalAssetRepository.findByUuid(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private VerificationEvidence latestEvidence(DigitalAsset asset) {
        return evidenceRepository.findFirstByAssetOrderByGeneratedAtDesc(asset)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Verification evidence has not been generated"));
    }

    private PendingVerificationResponse toPending(
            DigitalAsset asset,
            VerificationEvidence evidence) {
        return new PendingVerificationResponse(
                asset.getUuid(),
                asset.getTitle(),
                asset.getAssetType().name(),
                asset.getVerificationStatus().name(),
                evidence.getUuid(),
                evidence.getGeneratedAt());
    }

    private AuthenticatorReviewResponse toReviewResponse(AuthenticatorReview review) {
        return new AuthenticatorReviewResponse(
                review.getUuid(),
                review.getEvidence().getAsset().getUuid(),
                review.getEvidence().getUuid(),
                review.getDecision().name(),
                review.getReason(),
                review.getReviewer().getUuid(),
                review.getReviewedAt());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
