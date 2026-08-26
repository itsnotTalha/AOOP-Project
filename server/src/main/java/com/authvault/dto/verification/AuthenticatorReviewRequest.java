package com.authvault.dto.verification;

import jakarta.validation.constraints.Size;

public record AuthenticatorReviewRequest(
        @Size(max = 2000, message = "Reason must not exceed 2000 characters")
        String reason) {
}
