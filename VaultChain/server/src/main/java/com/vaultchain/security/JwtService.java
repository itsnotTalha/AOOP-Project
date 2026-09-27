package com.vaultchain.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.vaultchain.exception.ApiException;
import com.vaultchain.model.UserRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String secret;
    private final String expiry;
    private final Clock clock;

    public JwtService(@Value("${vaultchain.jwt-secret}") String secret,
                      @Value("${vaultchain.jwt-expires-in}") String expiry, Clock clock) {
        this.secret = secret == null || secret.isEmpty() ? "vaultchain-development-secret" : secret;
        this.expiry = expiry;
        this.clock = clock;
    }

    public String sign(UserRecord user) {
        long now = clock.instant().getEpochSecond();
        return JWT.create().withClaim("id", user.id()).withClaim("email", user.email())
                .withClaim("role", user.role()).withClaim("status", user.status())
                .withJWTId(UUID.randomUUID().toString()).withIssuedAt(Instant.ofEpochSecond(now))
                .withExpiresAt(Instant.ofEpochSecond(JwtExpiry.expiresAt(now, expiry)))
                .sign(Algorithm.HMAC256(secret));
    }

    public DecodedJWT verify(String token) {
        try {
            Algorithm algorithm = switch (JWT.decode(token).getAlgorithm()) {
                case "HS256" -> Algorithm.HMAC256(secret);
                case "HS384" -> Algorithm.HMAC384(secret);
                case "HS512" -> Algorithm.HMAC512(secret);
                default -> throw new IllegalArgumentException("Unsupported JWT algorithm");
            };
            // Node verifies exp/nbf but does not reject future iat or require exp/jti on old tokens.
            var verification = (com.auth0.jwt.JWTVerifier.BaseVerification) JWT.require(algorithm).ignoreIssuedAt();
            return verification.build(clock).verify(token);
        } catch (RuntimeException invalid) {
            throw new ApiException(401, "Invalid or expired token");
        }
    }

    public static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
