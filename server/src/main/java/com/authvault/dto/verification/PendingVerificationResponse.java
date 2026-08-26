package com.authvault.dto.verification;

import java.time.LocalDateTime;

public record PendingVerificationResponse(
        String assetId,
        String title,
        String assetType,
        String verificationStatus,
        String evidenceId,
        LocalDateTime evidenceGeneratedAt) {
}
