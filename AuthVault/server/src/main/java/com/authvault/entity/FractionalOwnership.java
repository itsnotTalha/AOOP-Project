package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "fractional_ownership")
public class FractionalOwnership {
    protected FractionalOwnership() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "percentage")
    private Double percentage;

    @Column(name = "shares")
    private Long shares;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Double getPercentage() { return percentage; }
    public void setPercentage(Double value) { this.percentage = value; }
    public Long getShares() { return shares; }
    public void setShares(Long value) { this.shares = value; }
}
