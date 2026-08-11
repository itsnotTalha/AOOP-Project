package com.authvault.repository;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.VerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationHistoryRepository extends JpaRepository<VerificationHistory, Long> {

    List<VerificationHistory> findByAssetId(Long assetId);

    List<VerificationHistory> findByAsset(DigitalAsset asset);
}
