package com.authvault.blockchain;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;

import java.util.List;
import java.util.Optional;

public interface OriginalRegistryClient {

    boolean isEnabled();

    BlockchainRegistrationResponse registerOriginal(RegistrationRequest request);

    BlockchainOriginalResponse getOriginal(String assetId);

    Optional<BlockchainOriginalResponse> findBySha256(String sha256);

    List<BlockchainHistoryResponse> getAssetHistory(String assetId);

    record RegistrationRequest(
            String assetId,
            String creatorIdHash,
            String sha256,
            String assetType,
            String verificationStatus,
            String evidenceHash) {
    }
}
