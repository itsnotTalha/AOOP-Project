package com.vaultchain.entity;

import java.io.Serializable;
import java.util.Objects;

public class VaultAssetId implements Serializable {
    public Long vaultId;
    public Long assetId;
    public VaultAssetId() {}
    @Override public boolean equals(Object object) {
        if (!(object instanceof VaultAssetId other)) return false;
        return Objects.equals(vaultId, other.vaultId) && Objects.equals(assetId, other.assetId);
    }
    @Override public int hashCode() { return Objects.hash(vaultId, assetId); }
}
