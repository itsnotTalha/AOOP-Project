package com.authvault.blockchain;

import com.authvault.service.Sha256Service;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class CanonicalSha256Hasher {

    private final Sha256Service sha256Service;

    public CanonicalSha256Hasher(Sha256Service sha256Service) {
        this.sha256Service = sha256Service;
    }

    public String hashUtf8(String canonicalValue) {
        if (canonicalValue == null) {
            throw new IllegalArgumentException("Canonical value is required");
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(
                canonicalValue.getBytes(StandardCharsets.UTF_8))) {
            return sha256Service.calculate(inputStream).hash();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not hash canonical blockchain data", exception);
        }
    }
}
