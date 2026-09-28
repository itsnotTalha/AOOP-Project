package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "platform_settings")
public class PlatformSetting {
    public PlatformSetting() {}

    @Id
    @Column(name = "setting_key")
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private String updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User updatedByEntity;

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String value) { this.settingKey = value; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String value) { this.settingValue = value; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long value) { this.updatedBy = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String value) { this.updatedAt = value; }
}
