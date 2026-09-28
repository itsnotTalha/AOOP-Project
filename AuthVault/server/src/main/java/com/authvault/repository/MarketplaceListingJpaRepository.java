package com.authvault.repository;

import com.authvault.entity.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceListingJpaRepository extends JpaRepository<MarketplaceListing, Long> {
 java.util.Optional<MarketplaceListing> findByPublicReference(String reference);
 java.util.List<MarketplaceListing> findAllByOrderByCreatedAtDescIdDesc();
 boolean existsByAssetIdAndStatus(Long assetId,String status);
}
