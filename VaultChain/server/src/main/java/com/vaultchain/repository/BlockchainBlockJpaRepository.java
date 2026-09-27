package com.vaultchain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vaultchain.entity.BlockchainBlock;

public interface BlockchainBlockJpaRepository extends JpaRepository<BlockchainBlock, Long> {
    Optional<BlockchainBlock> findTopByOrderByBlockIndexDesc();
    List<BlockchainBlock> findByAssetIdOrderByBlockIndexDesc(Long assetId);
    List<BlockchainBlock> findAllByOrderByBlockIndexDesc();
    Optional<BlockchainBlock> findByBlockIndex(Long blockIndex);
}
