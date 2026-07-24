package com.verivault.repository;

import com.verivault.entity.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {

    List<MarketplaceListing> findByStatus(MarketplaceListing.Status status);

    List<MarketplaceListing> findBySellerId(Long sellerId);

    Optional<MarketplaceListing> findByAssetId(Long assetId);
}
