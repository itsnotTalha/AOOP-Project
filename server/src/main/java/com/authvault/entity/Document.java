package com.authvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @OneToOne
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    @ToString.Exclude
    private DigitalAsset asset;

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "semantic_hash")
    private String semanticHash;

    @Column(name = "page_count")
    private Integer pageCount;

    private String language;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
