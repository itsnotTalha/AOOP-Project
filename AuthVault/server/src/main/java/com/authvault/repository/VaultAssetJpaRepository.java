package com.authvault.repository;

import com.authvault.entity.VaultAsset;
import com.authvault.entity.VaultAssetId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultAssetJpaRepository extends JpaRepository<VaultAsset, VaultAssetId> {
    java.util.List<VaultAsset> findByVaultIdOrderByAddedAtDescAssetIdDesc(Long vaultId);
    java.util.List<VaultAsset> findByAssetIdOrderByVaultIdAsc(Long assetId);
    boolean existsByVaultIdAndAssetId(Long vaultId,Long assetId);
    void deleteByVaultIdAndAssetId(Long vaultId,Long assetId);
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("delete from VaultAsset v where v.assetId = :assetId and v.vaultId in (select t.id from Vault t where t.userId = :userId)")
 void deleteSellerMemberships(Long assetId,Long userId);
}
