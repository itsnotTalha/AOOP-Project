package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "assets")
public class Asset {
    public Asset() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User owner;

    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    private java.util.List<VaultAsset> vaultMemberships = new java.util.ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { this.category = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { this.fileName = value; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String value) { this.filePath = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { this.fileSize = value; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String value) { this.mimeType = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String value) { this.updatedAt = value; }
}
