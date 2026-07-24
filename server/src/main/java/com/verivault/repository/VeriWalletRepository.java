package com.verivault.repository;

import com.verivault.entity.VeriWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VeriWalletRepository extends JpaRepository<VeriWallet, Long> {

    Optional<VeriWallet> findByUserId(Long userId);
}
