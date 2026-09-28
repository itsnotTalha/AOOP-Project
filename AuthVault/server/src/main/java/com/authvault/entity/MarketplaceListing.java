package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "marketplace_listings")
public class MarketplaceListing {
    public MarketplaceListing() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "public_reference")
    private String publicReference;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "listing_type")
    private String listingType;

    @Column(name = "price")
    private Double price;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @Column(name = "sold_at")
    private String soldAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getPublicReference() { return publicReference; }
    public void setPublicReference(String value) { this.publicReference = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long value) { this.sellerId = value; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long value) { this.buyerId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getListingType() { return listingType; }
    public void setListingType(String value) { this.listingType = value; }
    public Double getPrice() { return price; }
    public void setPrice(Double value) { this.price = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
    public String getSoldAt() { return soldAt; }
    public void setSoldAt(String value) { this.soldAt = value; }
}
