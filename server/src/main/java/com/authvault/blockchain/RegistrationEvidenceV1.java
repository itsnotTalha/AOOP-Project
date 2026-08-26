package com.authvault.blockchain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record RegistrationEvidenceV1(
        String assetId,
        String assetType,
        String sha256,
        String verificationStatus,
        LocalDateTime lastVerifiedAt) {

    public static final String SCHEMA = "AUTHVAULT_REGISTRATION_EVIDENCE_V1";

    public RegistrationEvidenceV1 {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        Objects.requireNonNull(lastVerifiedAt, "lastVerifiedAt");
    }

    public String canonicalForm() {
        return String.join("\n",
                SCHEMA,
                "assetId=" + assetId,
                "assetType=" + assetType,
                "sha256=" + sha256,
                "verificationStatus=" + verificationStatus,
                "lastVerifiedAt=" + lastVerifiedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
