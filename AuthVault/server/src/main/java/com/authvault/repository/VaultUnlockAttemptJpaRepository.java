package com.authvault.repository;

import com.authvault.entity.VaultUnlockAttempt;
import com.authvault.entity.VaultUnlockAttemptId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultUnlockAttemptJpaRepository extends JpaRepository<VaultUnlockAttempt, VaultUnlockAttemptId> {
    java.util.Optional<VaultUnlockAttempt> findByVaultIdAndUserId(Long vaultId,Long userId);
    void deleteByVaultIdAndUserId(Long vaultId,Long userId);
}
