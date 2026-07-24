package com.verivault.repository;

import com.verivault.entity.SecureVault;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecureVaultRepository extends JpaRepository<SecureVault, Long> {

    List<SecureVault> findByUserId(Long userId);
}
