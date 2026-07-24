package com.verivault.repository;

import com.verivault.entity.BlockchainLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockchainLedgerRepository extends JpaRepository<BlockchainLedger, Long> {

    List<BlockchainLedger> findByAssetIdOrderByBlockIndexAsc(Long assetId);

    Optional<BlockchainLedger> findTopByOrderByBlockIndexDesc();
}
