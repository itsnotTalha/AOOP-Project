package com.vaultchain.entity;

import java.io.Serializable;
import java.util.Objects;

public class VaultUnlockAttemptId implements Serializable {
    public Long vaultId;
    public Long userId;
    public VaultUnlockAttemptId() {}
    @Override public boolean equals(Object object) {
        if (!(object instanceof VaultUnlockAttemptId other)) return false;
        return Objects.equals(vaultId, other.vaultId) && Objects.equals(userId, other.userId);
    }
    @Override public int hashCode() { return Objects.hash(vaultId, userId); }
}
