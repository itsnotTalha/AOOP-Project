package com.authvault.entity;

import java.io.Serializable;
import java.util.Objects;

public class VaultUnlockSessionId implements Serializable {
    public Long vaultId;
    public String tokenFingerprint;
    public VaultUnlockSessionId() {}
    @Override public boolean equals(Object object) {
        if (!(object instanceof VaultUnlockSessionId other)) return false;
        return Objects.equals(vaultId, other.vaultId) && Objects.equals(tokenFingerprint, other.tokenFingerprint);
    }
    @Override public int hashCode() { return Objects.hash(vaultId, tokenFingerprint); }
}
