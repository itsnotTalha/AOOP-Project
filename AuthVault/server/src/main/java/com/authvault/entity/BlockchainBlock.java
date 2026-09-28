package com.authvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "blockchain_blocks")
public class BlockchainBlock {
    public BlockchainBlock() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "block_index", nullable = false)
    private Long blockIndex;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "previous_hash")
    private String previousHash;

    @Column(name = "current_hash")
    private String currentHash;

    @Column(name = "created_at")
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getBlockIndex() { return blockIndex; }
    public void setBlockIndex(Long value) { this.blockIndex = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String value) { this.previousHash = value; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String value) { this.currentHash = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
