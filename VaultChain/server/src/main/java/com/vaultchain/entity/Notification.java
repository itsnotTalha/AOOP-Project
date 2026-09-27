package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class Notification {
    protected Notification() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "is_read")
    private Long isRead;

    @Column(name = "created_at")
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { this.message = value; }
    public Long getIsRead() { return isRead; }
    public void setIsRead(Long value) { this.isRead = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
