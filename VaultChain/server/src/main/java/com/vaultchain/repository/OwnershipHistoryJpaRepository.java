package com.vaultchain.repository;

import com.vaultchain.entity.OwnershipHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnershipHistoryJpaRepository extends JpaRepository<OwnershipHistory, Long> {
    java.util.List<OwnershipHistory> findByAssetIdOrderByTransferredAtDescIdDesc(Long assetId);
}
