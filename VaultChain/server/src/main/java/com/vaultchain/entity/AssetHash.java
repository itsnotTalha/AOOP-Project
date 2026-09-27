package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "asset_hashes")
public class AssetHash {
    public AssetHash() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "sha256_hash")
    private String sha256Hash;

    @Column(name = "phash")
    private String phash;

    @Column(name = "created_at")
    private String createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String value) { this.sha256Hash = value; }
    public String getPhash() { return phash; }
    public void setPhash(String value) { this.phash = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
