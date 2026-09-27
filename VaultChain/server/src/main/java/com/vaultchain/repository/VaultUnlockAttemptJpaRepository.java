package com.vaultchain.repository;

import com.vaultchain.entity.VaultUnlockAttempt;
import com.vaultchain.entity.VaultUnlockAttemptId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultUnlockAttemptJpaRepository extends JpaRepository<VaultUnlockAttempt, VaultUnlockAttemptId> {
    java.util.Optional<VaultUnlockAttempt> findByVaultIdAndUserId(Long vaultId,Long userId);
    void deleteByVaultIdAndUserId(Long vaultId,Long userId);
}
