package com.authvault.blockchain;

import com.authvault.entity.User;
import com.authvault.service.impl.Sha256ServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatorIdHasherTest {

    private final CreatorIdHasher hasher =
            new CreatorIdHasher(new CanonicalSha256Hasher(new Sha256ServiceImpl()));

    @Test
    void hashesStableUuidDeterministicallyWithoutUsingPersonalFields() {
        User first = user("11111111-1111-1111-1111-111111111111", "first@example.com");
        User sameStableIdentity = user(
                "11111111-1111-1111-1111-111111111111", "changed@example.com");
        User different = user("22222222-2222-2222-2222-222222222222", "first@example.com");

        String firstHash = hasher.hash(first);

        assertThat(firstHash).matches("^[0-9a-f]{64}$");
        assertThat(hasher.hash(sameStableIdentity)).isEqualTo(firstHash);
        assertThat(hasher.hash(different)).isNotEqualTo(firstHash);
        assertThat(firstHash).doesNotContain(first.getUuid(), first.getEmail());
    }

    @Test
    void rejectsMissingStableUuid() {
        assertThatThrownBy(() -> hasher.hash(new User()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stable user UUID is required");
    }

    private User user(String uuid, String email) {
        User user = new User();
        user.setUuid(uuid);
        user.setEmail(email);
        return user;
    }
}
