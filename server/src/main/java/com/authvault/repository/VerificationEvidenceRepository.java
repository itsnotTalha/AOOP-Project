package com.authvault.repository;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.VerificationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationEvidenceRepository
        extends JpaRepository<VerificationEvidence, Long> {

    Optional<VerificationEvidence> findFirstByAssetOrderByGeneratedAtDesc(DigitalAsset asset);

    List<VerificationEvidence> findByAssetOrderByGeneratedAtDesc(DigitalAsset asset);
}
