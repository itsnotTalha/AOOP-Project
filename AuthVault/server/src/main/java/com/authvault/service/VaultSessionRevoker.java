package com.authvault.service;

/** Logout's narrow integration point; later Vault services share this persistent session store. */
public interface VaultSessionRevoker {
    void revokeTokenAccess(long userId, String tokenFingerprint);
}
