package com.authvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VerificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    @ToString.Exclude
    private DigitalAsset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verified_by", nullable = false)
    @ToString.Exclude
    private User verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false)
    private VerificationMethod verificationMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    @Column(name = "similarity_score", precision = 5, scale = 4)
    private BigDecimal similarityScore;

    private String notes;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    public enum Result {
        PENDING,
        VERIFIED,
        REJECTED,
        INCONCLUSIVE
    }

    public enum VerificationMethod {
        SHA256_UPLOAD,
        SHA256_INTEGRITY
    }
}
