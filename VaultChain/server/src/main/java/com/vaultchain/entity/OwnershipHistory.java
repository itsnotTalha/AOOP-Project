package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ownership_history")
public class OwnershipHistory {
    public OwnershipHistory() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "previous_owner")
    private Long previousOwner;

    @Column(name = "new_owner")
    private Long newOwner;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "price")
    private Double price;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "blockchain_block_id")
    private Long blockchainBlockId;

    @Column(name = "transferred_at", insertable = false, updatable = false)
    private String transferredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blockchain_block_id", referencedColumnName = "id", insertable = false, updatable = false)
    private BlockchainBlock blockchainBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", referencedColumnName = "id", insertable = false, updatable = false)
    private MarketplaceListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_owner", referencedColumnName = "id", insertable = false, updatable = false)
    private User newOwnerEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_owner", referencedColumnName = "id", insertable = false, updatable = false)
    private User previousOwnerEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getPreviousOwner() { return previousOwner; }
    public void setPreviousOwner(Long value) { this.previousOwner = value; }
    public Long getNewOwner() { return newOwner; }
    public void setNewOwner(Long value) { this.newOwner = value; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long value) { this.listingId = value; }
    public Double getPrice() { return price; }
    public void setPrice(Double value) { this.price = value; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String value) { this.transactionReference = value; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String value) { this.transferType = value; }
    public Long getBlockchainBlockId() { return blockchainBlockId; }
    public void setBlockchainBlockId(Long value) { this.blockchainBlockId = value; }
    public String getTransferredAt() { return transferredAt; }
    public void setTransferredAt(String value) { this.transferredAt = value; }
}
