package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vault_items")
public class VaultItem {
    protected VaultItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "encrypted_path", nullable = false)
    private String encryptedPath;

    @Column(name = "encryption_algorithm")
    private String encryptionAlgorithm;

    @Column(name = "created_at")
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User owner;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getEncryptedPath() { return encryptedPath; }
    public void setEncryptedPath(String value) { this.encryptedPath = value; }
    public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
    public void setEncryptionAlgorithm(String value) { this.encryptionAlgorithm = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
