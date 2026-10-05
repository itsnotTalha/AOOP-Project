package com.authvault.repository;

import com.authvault.entity.VaultUnlockSession;
import com.authvault.entity.VaultUnlockSessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultUnlockSessionJpaRepository extends JpaRepository<VaultUnlockSession, VaultUnlockSessionId> {
    java.util.Optional<VaultUnlockSession> findByVaultIdAndUserIdAndTokenFingerprint(Long vaultId,Long userId,String fingerprint);
    void deleteByVaultIdAndUserIdAndTokenFingerprint(Long vaultId,Long userId,String fingerprint);
    void deleteByVaultId(Long vaultId);
    void deleteByUserIdAndTokenFingerprint(Long userId, String tokenFingerprint);
}
