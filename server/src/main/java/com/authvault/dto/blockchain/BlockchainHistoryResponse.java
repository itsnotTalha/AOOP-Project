package com.authvault.dto.blockchain;

import java.time.Instant;

public record BlockchainHistoryResponse(
        String transactionId,
        Instant timestamp,
        boolean isDelete,
        BlockchainOriginalResponse asset) {
}
