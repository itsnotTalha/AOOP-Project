package com.authvault.repository;

import com.authvault.service.VaultSessionRevoker;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaVaultSessionRevoker implements VaultSessionRevoker {
    private final VaultUnlockSessionJpaRepository sessions;
    public JpaVaultSessionRevoker(VaultUnlockSessionJpaRepository sessions) { this.sessions = sessions; }

    @Override
    @Transactional
    public void revokeTokenAccess(long userId, String tokenFingerprint) {
        sessions.deleteByUserIdAndTokenFingerprint(userId, tokenFingerprint);
    }
}
