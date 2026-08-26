package com.authvault.blockchain;

import com.authvault.service.impl.Sha256ServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationEvidenceHasherTest {

    private final RegistrationEvidenceHasher hasher =
            new RegistrationEvidenceHasher(new CanonicalSha256Hasher(new Sha256ServiceImpl()));

    @Test
    void usesFixedVersionedFieldOrderAndTimestampRepresentation() {
        RegistrationEvidenceV1 evidence = new RegistrationEvidenceV1(
                "11111111-1111-1111-1111-111111111111",
                "IMAGE",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "VERIFIED",
                LocalDateTime.of(2026, 8, 15, 9, 10, 11, 123_000_000));

        assertThat(evidence.canonicalForm()).isEqualTo("""
                AUTHVAULT_REGISTRATION_EVIDENCE_V1
                assetId=11111111-1111-1111-1111-111111111111
                assetType=IMAGE
                sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                verificationStatus=VERIFIED
                lastVerifiedAt=2026-08-15T09:10:11.123""");
        assertThat(hasher.hash(evidence)).matches("^[0-9a-f]{64}$");
        assertThat(hasher.hash(evidence)).isEqualTo(hasher.hash(evidence));
    }

    @Test
    void changingTrustedEvidenceChangesTheHash() {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 15, 9, 10, 11);
        RegistrationEvidenceV1 image = new RegistrationEvidenceV1(
                "11111111-1111-1111-1111-111111111111",
                "IMAGE",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "VERIFIED",
                verifiedAt);
        RegistrationEvidenceV1 document = new RegistrationEvidenceV1(
                image.assetId(), "DOCUMENT", image.sha256(), image.verificationStatus(), verifiedAt);

        assertThat(hasher.hash(document)).isNotEqualTo(hasher.hash(image));
    }
}
