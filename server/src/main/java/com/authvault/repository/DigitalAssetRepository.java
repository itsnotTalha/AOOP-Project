package com.authvault.repository;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, Long>,
        JpaSpecificationExecutor<DigitalAsset> {

    Optional<DigitalAsset> findByUuid(String uuid);

    Optional<DigitalAsset> findByUuidAndCurrentOwner(String uuid, User currentOwner);

    Optional<DigitalAsset> findBySha256Hash(String sha256Hash);

    List<DigitalAsset> findByOwnerId(Long ownerId);

    List<DigitalAsset> findByCurrentOwnerId(Long currentOwnerId);

    List<DigitalAsset> findByCurrentOwnerOrderByUploadDateDesc(User currentOwner);

    List<DigitalAsset> findByVerificationStatus(DigitalAsset.VerificationStatus verificationStatus);

    boolean existsBySha256Hash(String sha256Hash);

    @Query("""
            SELECT asset
            FROM DigitalAsset asset
            WHERE asset.currentOwner = :owner
              AND asset.assetType = com.authvault.entity.DigitalAsset.AssetType.IMAGE
              AND asset.uuid <> :targetUuid
              AND asset.uploadDate < :targetUploadDate
            """)
    List<DigitalAsset> findPreviousOwnedImages(
            @Param("owner") User owner,
            @Param("targetUuid") String targetUuid,
            @Param("targetUploadDate") LocalDateTime targetUploadDate);
}
