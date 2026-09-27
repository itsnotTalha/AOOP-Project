package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "document_verifications")
public class DocumentVerification {
    public DocumentVerification() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "reference_document_id", nullable = false)
    private Long referenceDocumentId;

    @Column(name = "semantic_hash_match")
    private Long semanticHashMatch;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "report_json", nullable = false)
    private String reportJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_document_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Document referenceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { this.documentId = value; }
    public Long getReferenceDocumentId() { return referenceDocumentId; }
    public void setReferenceDocumentId(Long value) { this.referenceDocumentId = value; }
    public Long getSemanticHashMatch() { return semanticHashMatch; }
    public void setSemanticHashMatch(Long value) { this.semanticHashMatch = value; }
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double value) { this.similarityScore = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String value) { this.reportJson = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
