package com.authvault.repository;

import com.authvault.entity.AssetMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMetadataJpaRepository extends JpaRepository<AssetMetadata, Long> {
    java.util.Optional<AssetMetadata> findByAssetId(Long assetId);
}
