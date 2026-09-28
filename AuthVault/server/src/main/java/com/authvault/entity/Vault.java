package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vaults")
public class Vault {
    public Vault() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "public_reference", nullable = false)
    private String publicReference;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auto_lock_minutes", nullable = false)
    private Long autoLockMinutes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @OneToMany(mappedBy = "vault", fetch = FetchType.LAZY)
    private java.util.List<VaultAsset> memberships = new java.util.ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public String getPublicReference() { return publicReference; }
    public void setPublicReference(String value) { this.publicReference = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash = value; }
    public Long getAutoLockMinutes() { return autoLockMinutes; }
    public void setAutoLockMinutes(Long value) { this.autoLockMinutes = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String value) { this.updatedAt = value; }
}
