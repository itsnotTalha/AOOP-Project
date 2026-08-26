package com.authvault.service;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;

import java.util.List;

public interface BlockchainRegistryService {

    BlockchainRegistrationResponse registerOwnedAsset(String assetId);

    BlockchainOriginalResponse getOwnedAssetRegistration(String assetId);

    List<BlockchainHistoryResponse> getOwnedAssetHistory(String assetId);

    BlockchainOriginalLookupResponse findBySha256(String sha256);
}
