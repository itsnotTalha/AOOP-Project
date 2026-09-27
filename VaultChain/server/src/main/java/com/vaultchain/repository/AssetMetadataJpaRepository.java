package com.vaultchain.repository;

import com.vaultchain.entity.AssetMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMetadataJpaRepository extends JpaRepository<AssetMetadata, Long> {
    java.util.Optional<AssetMetadata> findByAssetId(Long assetId);
}
