package com.authvault.dto.blockchain;

public record BlockchainRegistrationResponse(
        String transactionId,
        BlockchainOriginalResponse asset) {
}
