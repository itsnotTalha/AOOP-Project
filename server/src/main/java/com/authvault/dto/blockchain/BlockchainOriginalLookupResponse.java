package com.authvault.dto.blockchain;

public record BlockchainOriginalLookupResponse(
        boolean found,
        BlockchainOriginalResponse asset) {
}
