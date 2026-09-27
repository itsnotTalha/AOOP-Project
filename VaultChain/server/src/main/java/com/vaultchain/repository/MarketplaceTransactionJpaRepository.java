package com.vaultchain.repository;

import com.vaultchain.entity.MarketplaceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceTransactionJpaRepository extends JpaRepository<MarketplaceTransaction, Long> {}
