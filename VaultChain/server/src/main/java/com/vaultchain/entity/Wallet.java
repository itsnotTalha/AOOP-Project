package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "wallets")
public class Wallet {
    public Wallet() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Double getBalance() { return balance; }
    public void setBalance(Double value) { this.balance = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
