package com.authvault.dto.verification;

import java.time.LocalDateTime;

public record AuthenticatorReviewResponse(
        String reviewId,
        String assetId,
        String evidenceId,
        String decision,
        String reason,
        String reviewerUserId,
        LocalDateTime reviewedAt) {
}
