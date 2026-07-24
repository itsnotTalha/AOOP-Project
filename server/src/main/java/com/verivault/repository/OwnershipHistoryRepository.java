package com.verivault.repository;

import com.verivault.entity.OwnershipHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OwnershipHistoryRepository extends JpaRepository<OwnershipHistory, Long> {

    List<OwnershipHistory> findByAssetIdOrderByTransferDateAsc(Long assetId);
}
