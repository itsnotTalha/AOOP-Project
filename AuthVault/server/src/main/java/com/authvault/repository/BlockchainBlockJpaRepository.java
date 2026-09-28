package com.authvault.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.authvault.entity.BlockchainBlock;

public interface BlockchainBlockJpaRepository extends JpaRepository<BlockchainBlock, Long> {
    Optional<BlockchainBlock> findTopByOrderByBlockIndexDesc();
    List<BlockchainBlock> findByAssetIdOrderByBlockIndexDesc(Long assetId);
    List<BlockchainBlock> findAllByOrderByBlockIndexDesc();
    Optional<BlockchainBlock> findByBlockIndex(Long blockIndex);
}
