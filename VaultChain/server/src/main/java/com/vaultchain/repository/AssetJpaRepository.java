package com.vaultchain.repository;

import com.vaultchain.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetJpaRepository extends JpaRepository<Asset, Long> {
    java.util.Optional<Asset> findByIdAndOwnerId(Long id,Long ownerId);
    java.util.List<Asset> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
    long countByOwnerId(Long ownerId);
}
