package com.authvault.repository;

import com.authvault.entity.MarketplaceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceTransactionJpaRepository extends JpaRepository<MarketplaceTransaction, Long> {}
