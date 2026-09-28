package com.authvault.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ocr_results")
public class OcrResult {
    public OcrResult() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "semantic_hash")
    private String semanticHash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Document document;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { this.documentId = value; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String value) { this.extractedText = value; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double value) { this.confidence = value; }
    public String getSemanticHash() { return semanticHash; }
    public void setSemanticHash(String value) { this.semanticHash = value; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String value) { this.createdAt = value; }
}
