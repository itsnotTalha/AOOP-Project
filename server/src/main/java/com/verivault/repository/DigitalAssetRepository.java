package com.verivault.repository;

import com.verivault.entity.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, Long> {

    Optional<DigitalAsset> findByUuid(String uuid);

    Optional<DigitalAsset> findBySha256Hash(String sha256Hash);

    List<DigitalAsset> findByOwnerId(Long ownerId);

    List<DigitalAsset> findByCurrentOwnerId(Long currentOwnerId);

    List<DigitalAsset> findByVerificationStatus(DigitalAsset.VerificationStatus verificationStatus);

    boolean existsBySha256Hash(String sha256Hash);
}
