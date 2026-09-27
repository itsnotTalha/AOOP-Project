package com.vaultchain.repository;

import com.vaultchain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {
    java.util.Optional<Wallet> findByUserId(Long userId);
}
