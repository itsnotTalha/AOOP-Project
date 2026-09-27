package com.vaultchain.repository;

import com.vaultchain.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, Long> {
    java.util.List<WalletTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId);
}
