package com.authvault.blockchain;

import org.springframework.stereotype.Component;

@Component
public class RegistrationEvidenceHasher {

    private final CanonicalSha256Hasher hasher;

    public RegistrationEvidenceHasher(CanonicalSha256Hasher hasher) {
        this.hasher = hasher;
    }

    public String hash(RegistrationEvidenceV1 evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Registration evidence is required");
        }
        return hasher.hashUtf8(evidence.canonicalForm());
    }
}
