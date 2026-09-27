package com.vaultchain.entity;

import jakarta.persistence.*;

@IdClass(VaultAssetId.class)
@Entity
@Table(name = "vault_assets")
public class VaultAsset {
    public VaultAsset() {}

    @Id
    @Column(name = "vault_id")
    private Long vaultId;

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "added_at", insertable = false, updatable = false)
    private String addedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Vault vault;

    public Long getVaultId() { return vaultId; }
    public void setVaultId(Long value) { this.vaultId = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public String getAddedAt() { return addedAt; }
    public void setAddedAt(String value) { this.addedAt = value; }
}
