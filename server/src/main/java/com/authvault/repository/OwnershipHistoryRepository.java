package com.authvault.repository;

import com.authvault.entity.OwnershipHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OwnershipHistoryRepository extends JpaRepository<OwnershipHistory, Long> {

    List<OwnershipHistory> findByAssetIdOrderByTransferDateAsc(Long assetId);
}
