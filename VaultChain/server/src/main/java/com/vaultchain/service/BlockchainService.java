package com.vaultchain.service;

import java.util.List;
import java.util.Map;
import com.vaultchain.entity.BlockchainBlock;

public interface BlockchainService {
    BlockchainBlock recordBlock(Long assetId, Long ownerId, String action, String dataPayload);
    Map<String, Object> getVerificationFlow(Long assetId, String currentHash);
    List<Map<String, Object>> getBlocks(int limit);
    Map<String, Object> getBlockByIndex(long blockIndex);
    List<Map<String, Object>> getBlocksForAsset(Long assetId);
    Map<String, Object> getStats();
    boolean verifyChainIntegrity();
}
