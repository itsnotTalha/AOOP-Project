package com.verivault.repository;

import com.verivault.entity.VerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationHistoryRepository extends JpaRepository<VerificationHistory, Long> {

    List<VerificationHistory> findByAssetId(Long assetId);
}
