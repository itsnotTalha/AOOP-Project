package com.authvault.dto.verification;

public record AuthenticatorReviewDetailResponse(
        VerificationEvidenceResponse evidence,
        AuthenticatorReviewResponse review) {
}
