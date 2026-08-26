package com.authvault.dto.blockchain;

import java.time.Instant;

public record BlockchainOriginalResponse(
        String assetId,
        String creatorIdHash,
        String currentOwnerIdHash,
        String sha256,
        String assetType,
        String verificationStatus,
        String evidenceHash,
        Instant registeredAt) {
}
