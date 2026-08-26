package com.authvault.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_evidence")
@Getter
@Setter
@NoArgsConstructor
public class VerificationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private DigitalAsset asset;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "exact_duplicate_detected", nullable = false)
    private boolean exactDuplicateDetected;

    @Column(name = "perceptual_hash", length = 16)
    private String perceptualHash;

    @Column(name = "perceptual_hash_status", nullable = false)
    private String perceptualHashStatus;

    @Column(name = "similar_candidate_count")
    private Integer similarCandidateCount;

    @Column(name = "best_candidate_asset_uuid")
    private String bestCandidateAssetUuid;

    @Column(name = "best_phash_distance")
    private Integer bestPhashDistance;

    @Column(name = "comparison_performed", nullable = false)
    private boolean comparisonPerformed;

    @Column(name = "comparison_status")
    private String comparisonStatus;

    @Column(name = "comparison_reason")
    private String comparisonReason;

    @Column(name = "changed_area_ratio")
    private Double changedAreaRatio;

    @Column(name = "mean_absolute_difference")
    private Double meanAbsoluteDifference;

    @Column(name = "structural_similarity")
    private Double structuralSimilarity;

    @Column(name = "fabric_lookup_performed", nullable = false)
    private boolean fabricLookupPerformed;

    @Column(name = "fabric_status", nullable = false)
    private String fabricStatus;

    @Column(name = "fabric_registered_original_found")
    private Boolean fabricRegisteredOriginalFound;

    @Column(name = "fabric_reference_asset_uuid")
    private String fabricReferenceAssetUuid;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "evidence_version", nullable = false)
    private String evidenceVersion;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;

    @OneToOne(mappedBy = "evidence", cascade = CascadeType.ALL, orphanRemoval = true)
    private AuthenticatorReview review;
}
