package com.authvault.repository;

import com.authvault.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {
    java.util.Optional<Wallet> findByUserId(Long userId);
}
