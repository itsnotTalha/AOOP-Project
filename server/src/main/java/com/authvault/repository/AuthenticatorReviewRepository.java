package com.authvault.repository;

import com.authvault.entity.AuthenticatorReview;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.VerificationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthenticatorReviewRepository
        extends JpaRepository<AuthenticatorReview, Long> {

    boolean existsByEvidence_Asset(DigitalAsset asset);

    Optional<AuthenticatorReview> findByEvidence(VerificationEvidence evidence);
}
