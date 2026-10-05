package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "documents")
public class Document {
    public Document() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256_hash", nullable = false)
    private String sha256Hash;

    @Column(name = "page_count")
    private Long pageCount;

    @Column(name = "language")
    private String language;

    @Column(name = "ocr_status", nullable = false)
    private String ocrStatus;

    @Column(name = "ocr_error")
    private String ocrError;

    @Column(name = "ocr_processed_at")
    private String ocrProcessedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User owner;

    @OneToOne(mappedBy = "document", fetch = FetchType.LAZY)
    private OcrResult ocrResult;
    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    private java.util.List<DocumentVerification> verifications = new java.util.ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String value) { this.originalName = value; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String value) { this.storedName = value; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String value) { this.filePath = value; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String value) { this.mimeType = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { this.fileSize = value; }
    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String value) { this.sha256Hash = value; }
    public Long getPageCount() { return pageCount; }
    public void setPageCount(Long value) { this.pageCount = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { this.language = value; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String value) { this.ocrStatus = value; }
    public String getOcrError() { return ocrError; }
    public void setOcrError(String value) { this.ocrError = value; }
    public String getOcrProcessedAt() { return ocrProcessedAt; }
    public void setOcrProcessedAt(String value) { this.ocrProcessedAt = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
