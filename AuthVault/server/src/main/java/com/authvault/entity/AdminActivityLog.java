package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_activity_logs")
public class AdminActivityLog {
    public AdminActivityLog() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "details_json")
    private String detailsJson;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at")
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User admin;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long value) { this.adminId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { this.targetType = value; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String value) { this.targetId = value; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String value) { this.detailsJson = value; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String value) { this.ipAddress = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
