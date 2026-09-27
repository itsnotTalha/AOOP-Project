package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "marketplace_transactions")
public class MarketplaceTransaction {
    public MarketplaceTransaction() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "sale_amount", nullable = false)
    private Double saleAmount;

    @Column(name = "platform_fee", nullable = false)
    private Double platformFee;

    @Column(name = "seller_amount", nullable = false)
    private Double sellerAmount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at")
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", referencedColumnName = "id", insertable = false, updatable = false)
    private MarketplaceListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String value) { this.transactionId = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long value) { this.listingId = value; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long value) { this.sellerId = value; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long value) { this.buyerId = value; }
    public Double getSaleAmount() { return saleAmount; }
    public void setSaleAmount(Double value) { this.saleAmount = value; }
    public Double getPlatformFee() { return platformFee; }
    public void setPlatformFee(Double value) { this.platformFee = value; }
    public Double getSellerAmount() { return sellerAmount; }
    public void setSellerAmount(Double value) { this.sellerAmount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
