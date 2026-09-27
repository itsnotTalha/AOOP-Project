package com.vaultchain.repository;

import com.vaultchain.entity.VaultUnlockSession;
import com.vaultchain.entity.VaultUnlockSessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultUnlockSessionJpaRepository extends JpaRepository<VaultUnlockSession, VaultUnlockSessionId> {
    java.util.Optional<VaultUnlockSession> findByVaultIdAndUserIdAndTokenFingerprint(Long vaultId,Long userId,String fingerprint);
    void deleteByVaultIdAndUserIdAndTokenFingerprint(Long vaultId,Long userId,String fingerprint);
    void deleteByVaultId(Long vaultId);
    void deleteByUserIdAndTokenFingerprint(Long userId, String tokenFingerprint);
}
