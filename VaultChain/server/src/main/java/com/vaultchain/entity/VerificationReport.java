package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "verification_reports")
public class VerificationReport {
    public VerificationReport() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "verification_type")
    private String verificationType;

    @Column(name = "sha256_match")
    private Long sha256Match;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "status")
    private String status;

    @Column(name = "report_json")
    private String reportJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String value) { this.verificationType = value; }
    public Long getSha256Match() { return sha256Match; }
    public void setSha256Match(Long value) { this.sha256Match = value; }
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double value) { this.similarityScore = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String value) { this.reportJson = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
