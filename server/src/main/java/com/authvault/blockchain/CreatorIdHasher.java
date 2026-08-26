package com.authvault.blockchain;

import com.authvault.entity.User;
import org.springframework.stereotype.Component;

@Component
public class CreatorIdHasher {

    static final String DOMAIN_SEPARATOR = "AUTHVAULT_CREATOR_ID_V1:";

    private final CanonicalSha256Hasher hasher;

    public CreatorIdHasher(CanonicalSha256Hasher hasher) {
        this.hasher = hasher;
    }

    public String hash(User user) {
        if (user == null || user.getUuid() == null || user.getUuid().isBlank()) {
            throw new IllegalArgumentException("Stable user UUID is required");
        }
        return hasher.hashUtf8(DOMAIN_SEPARATOR + user.getUuid());
    }
}
