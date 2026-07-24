package com.verivault.repository;

import com.verivault.entity.FractionalOwnership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FractionalOwnershipRepository extends JpaRepository<FractionalOwnership, Long> {

    List<FractionalOwnership> findByAssetId(Long assetId);

    List<FractionalOwnership> findByOwnerId(Long ownerId);
}
