package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {
    public WalletTransaction() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "description")
    private String description;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Wallet wallet;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getWalletId() { return walletId; }
    public void setWalletId(Long value) { this.walletId = value; }
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
    public Double getAmount() { return amount; }
    public void setAmount(Double value) { this.amount = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String value) { this.referenceId = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
