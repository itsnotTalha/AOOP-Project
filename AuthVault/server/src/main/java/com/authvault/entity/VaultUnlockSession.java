package com.authvault.entity;

import jakarta.persistence.*;

@IdClass(VaultUnlockSessionId.class)
@Entity
@Table(name = "vault_unlock_sessions")
public class VaultUnlockSession {
    public VaultUnlockSession() {}

    @Id
    @Column(name = "vault_id")
    private Long vaultId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "token_fingerprint")
    private String tokenFingerprint;

    @Column(name = "expires_at", nullable = false)
    private String expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Vault vault;

    public Long getVaultId() { return vaultId; }
    public void setVaultId(Long value) { this.vaultId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public String getTokenFingerprint() { return tokenFingerprint; }
    public void setTokenFingerprint(String value) { this.tokenFingerprint = value; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String value) { this.expiresAt = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
