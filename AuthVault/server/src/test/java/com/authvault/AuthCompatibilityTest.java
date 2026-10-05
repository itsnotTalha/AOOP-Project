package com.authvault;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.authvault.security.*;
import com.authvault.model.UserRecord;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthCompatibilityTest {
    final ObjectMapper mapper = new ObjectMapper();
    JsonNode fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/auth/legacy-auth.json")) { return mapper.readTree(stream); }
    }
    @Test void acceptsActualNodeTokensAndFingerprints() throws Exception {
        var f = fixture();
        var service = new JwtService(f.path("secret").asText(), "7d", Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (String key : List.of("HS256", "HS384", "HS512", "futureIat", "withoutExp", "withoutId"))
            assertThat(service.verify(f.path("tokens").path(key).asText())).as(key).isNotNull();
        for (String key : List.of("expired", "futureNbf", "none"))
            assertThatThrownBy(() -> service.verify(f.path("tokens").path(key).asText())).as(key).hasMessage("Invalid or expired token");
        assertThatThrownBy(() -> service.verify("invalid")).hasMessage("Invalid or expired token");
        assertThatThrownBy(() -> new JwtService("wrong", "7d", Clock.systemUTC()).verify(f.path("tokens").path("HS256").asText())).hasMessage("Invalid or expired token");
        assertThat(JwtService.fingerprint(f.path("tokens").path("HS256").asText())).isEqualTo(f.path("tokenFingerprint").asText());
    }
    @Test void expiryMatchesNodeMsIncludingUnitlessMilliseconds() throws Exception {
        var f = fixture();
        for (var vector : f.path("expiry")) {
            String value = vector.path("value").asText();
            if (vector.has("error")) assertThatThrownBy(() -> JwtExpiry.expiresAt(f.path("issuedAt").asLong(), value)).hasMessage(vector.path("error").asText());
            else assertThat(JwtExpiry.expiresAt(f.path("issuedAt").asLong(), value)).as(value).isEqualTo(vector.path("expiresAt").asLong());
        }
    }
    @Test void bcryptMatchesNodeUnicodeNulAndSeventyTwoByteTruncationAndExportsJavaVectors() throws Exception {
        var f = fixture(); var passwords = new LegacyPasswordEncoder();
        var output = mapper.createObjectNode(); var vectors = output.putArray("passwords");
        for (var vector : f.path("passwords")) {
            String password = vector.path("password").asText();
            assertThat(passwords.matches(password, vector.path("hash").asText())).as(password).isTrue();
            var hash = passwords.encode(password);
            assertThat(hash).startsWith("$2b$10$");
            assertThat(passwords.matches(password, hash)).isTrue();
            vectors.addObject().put("password", password).put("hash", hash);
        }
        assertThat(passwords.matches("wrong", f.path("passwords").get(0).path("hash").asText())).isFalse();
        long now = 1790000000L;
        var service = new JwtService(f.path("secret").asText(), "7d", Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC));
        var user = new UserRecord(9001, "Java", "java@example.test", "unused", "USER", "active", "", "");
        String token = service.sign(user);
        assertThat(service.sign(user)).isNotEqualTo(token);
        output.put("secret", f.path("secret").asText()).put("issuedAt", now).put("token", token);
        Files.createDirectories(Path.of("target"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/auth-java-vectors.json").toFile(), output);
    }
}
