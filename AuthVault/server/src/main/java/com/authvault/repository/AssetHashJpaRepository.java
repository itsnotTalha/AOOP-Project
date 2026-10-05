package com.authvault.repository;

import com.authvault.entity.AssetHash;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetHashJpaRepository extends JpaRepository<AssetHash, Long> {
    java.util.Optional<AssetHash> findByAssetId(Long assetId);
    java.util.List<AssetHash> findByPhashOrderByAssetIdAsc(String phash);
    java.util.List<AssetHash> findBySha256HashOrderByAssetIdAsc(String sha256Hash);
    java.util.List<AssetHash> findByPhashIsNotNull();
}
