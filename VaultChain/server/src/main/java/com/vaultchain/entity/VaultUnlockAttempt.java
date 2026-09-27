package com.vaultchain.entity;

import jakarta.persistence.*;

@IdClass(VaultUnlockAttemptId.class)
@Entity
@Table(name = "vault_unlock_attempts")
public class VaultUnlockAttempt {
    public VaultUnlockAttempt() {}

    @Id
    @Column(name = "vault_id")
    private Long vaultId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "attempt_count", nullable = false)
    private Long attemptCount;

    @Column(name = "window_started_at", nullable = false)
    private String windowStartedAt;

    @Column(name = "blocked_until")
    private String blockedUntil;

    @Column(name = "updated_at")
    private String updatedAt;

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
    public Long getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Long value) { this.attemptCount = value; }
    public String getWindowStartedAt() { return windowStartedAt; }
    public void setWindowStartedAt(String value) { this.windowStartedAt = value; }
    public String getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(String value) { this.blockedUntil = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String value) { this.updatedAt = value; }
}
