package com.authvault.service;

import com.authvault.dto.verification.AuthenticatorReviewDetailResponse;
import com.authvault.dto.verification.AuthenticatorReviewResponse;
import com.authvault.dto.verification.PendingVerificationResponse;

import java.util.List;

public interface AuthenticatorReviewService {

    List<PendingVerificationResponse> getPendingReviews();

    AuthenticatorReviewDetailResponse getReview(String assetId);

    AuthenticatorReviewResponse approve(String assetId, String reason);

    AuthenticatorReviewResponse reject(String assetId, String reason);
}
