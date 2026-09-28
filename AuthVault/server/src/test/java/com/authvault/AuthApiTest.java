package com.authvault;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.authvault.security.JwtService;
import com.authvault.security.Role;
import com.authvault.security.TokenAuthentication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthApiTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("authvault.database-path", () -> directory.resolve("auth.sqlite").toString());
        r.add("authvault.jwt-secret", () -> "vaultchain-auth-compatibility-fixture-secret");
        r.add("authvault.jwt-expires-in", () -> "7d");
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenAuthentication authentication;
    @Autowired JwtService jwt;
    final HttpClient client = HttpClient.newHttpClient();
    record Response(int status, JsonNode body) {}
    Response request(String method, String path, Object body, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth" + path)).header("Content-Type", "application/json");
        if (authorization != null) builder.header("Authorization", authorization);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), mapper.readTree(response.body()));
    }
    JsonNode register() throws Exception {
        var response = request("POST", "/register", Map.of("fullName", " Test User ", "email", UUID.randomUUID() + "@example.test", "password", "Password123!", "role", "SUPER_ADMIN"), null);
        assertThat(response.status).isEqualTo(201); return response.body;
    }
    String bearer(JsonNode registration) { return "Bearer " + registration.path("token").asText(); }
    void error(Response response, int status, String message) {
        assertThat(response.status).isEqualTo(status);
        assertThat(response.body).isEqualTo(mapper.valueToTree(Map.of("success", false, "message", message)));
    }
    @Test void registrationLoginProfileAndMeMatchLegacyShapes() throws Exception {
        var account = register(); var user = account.path("user"); long id = user.path("id").asLong();
        assertThat(account.path("message").asText()).isEqualTo("User registered successfully");
        assertThat(user.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "fullName", "email", "role", "status", "createdAt", "updatedAt");
        assertThat(user.path("role").asText()).isEqualTo("USER");
        assertThat(user.path("createdAt").asText()).isNotBlank();
        assertThat(user.path("updatedAt").asText()).isNotBlank();
        assertThat(user.path("fullName").asText()).isEqualTo("Test User");
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE user_id=?", Double.class, id)).isZero();
        var login = request("POST", "/login", Map.of("email", " " + user.path("email").asText().toUpperCase(Locale.ROOT) + " ", "password", "Password123!"), null);
        assertThat(login.status).isEqualTo(200); assertThat(login.body.path("message").asText()).isEqualTo("Login successful");
        assertThat(login.body.path("token")).isNotEqualTo(account.path("token"));
        var claims = jwt.verify(account.path("token").asText());
        assertThat(claims.getExpiresAtAsInstant().getEpochSecond() - claims.getIssuedAtAsInstant().getEpochSecond()).isEqualTo(604800);
        assertThat(claims.getId()).isNotBlank();
        var me = request("GET", "/me/", null, bearer(account));
        assertThat(me.status).isEqualTo(200);
        assertThat(me.body.path("user").fieldNames()).toIterable().containsExactlyInAnyOrder("id", "full_name", "email", "role", "status", "created_at");
        String email = UUID.randomUUID() + "@updated.test";
        var profile = request("PATCH", "/profile", Map.of("full_name", " Updated ", "email", email.toUpperCase(Locale.ROOT)), bearer(account));
        assertThat(profile.status).isEqualTo(200);
        assertThat(profile.body.path("message").asText()).isEqualTo("Profile updated successfully");
        assertThat(profile.body.path("user").path("email").asText()).isEqualTo(email);
        assertThat(request("GET", "/me", null, bearer(account)).body.path("user").path("full_name").asText()).isEqualTo("Updated");
    }
    @Test void registrationAndProfileValidationAndDuplicateStatuses() throws Exception {
        error(request("POST", "/register", Map.of(), null), 400, "Full name is required");
        error(request("POST", "/register", Map.of("fullName", "Name"), null), 400, "Email is required");
        error(request("POST", "/register", Map.of("fullName", "Name", "email", "bad"), null), 400, "Email is invalid");
        error(request("POST", "/register", Map.of("fullName", "Name", "email", "a@b.c"), null), 400, "Password is required");
        error(request("POST", "/register", Map.of("fullName", "Name", "email", "a@b.c", "password", "short"), null), 400, "Password must be at least 8 characters long");
        var one = register(); var two = register();
        error(request("POST", "/register", Map.of("fullName", "Name", "email", one.path("user").path("email").asText(), "password", "Password123!"), null), 409, "Email is already registered");
        error(request("PATCH", "/profile", Map.of(), bearer(one)), 400, "Full name is required");
        error(request("PATCH", "/profile", Map.of("fullName", "a".repeat(101)), bearer(one)), 400, "Full name must be 100 characters or fewer");
        error(request("PATCH", "/profile", Map.of("fullName", "Name"), bearer(one)), 400, "Email is required");
        error(request("PATCH", "/profile", Map.of("fullName", "Name", "email", "not-an-email"), bearer(one)), 400, "Email is invalid");
        error(request("PATCH", "/profile", Map.of("fullName", "Name", "email", "a".repeat(250) + "@b.test"), bearer(one)), 400, "Email is invalid");
        error(request("PATCH", "/profile", Map.of("fullName", "Name", "email", two.path("user").path("email").asText()), bearer(one)), 409, "Email is already registered");
    }
    @Test void passwordChangeValidationsAndExistingTokenRemainsUsable() throws Exception {
        var account = register(); var token = bearer(account);
        error(request("PATCH", "/password", Map.of(), token), 400, "Current password is required");
        error(request("PATCH", "/password", Map.of("currentPassword", "Password123!"), token), 400, "New password is required");
        error(request("PATCH", "/password", Map.of("currentPassword", "Password123!", "newPassword", "short"), token), 400, "New password must be at least 8 characters long");
        error(request("PATCH", "/password", Map.of("currentPassword", "Password123!", "newPassword", "Password123!"), token), 400, "New password must be different from the current password");
        error(request("PATCH", "/password", Map.of("currentPassword", "wrong", "newPassword", "Replacement123!"), token), 401, "Current password is incorrect");
        var changed = request("PATCH", "/password", Map.of("currentPassword", "Password123!", "newPassword", "Replacement123!"), token);
        assertThat(changed.status).isEqualTo(200); assertThat(changed.body.path("message").asText()).isEqualTo("Password changed successfully");
        String email = account.path("user").path("email").asText();
        error(request("POST", "/login", Map.of("email", email, "password", "Password123!"), null), 401, "Invalid email or password");
        assertThat(request("POST", "/login", Map.of("email", email, "password", "Replacement123!"), null).status).isEqualTo(200);
        assertThat(request("GET", "/me", null, token).status).isEqualTo(200);
    }
    @Test void authenticationRefreshesDatabaseRoleStatusAndDeletedAccounts() throws Exception {
        var account = register(); long id = account.path("user").path("id").asLong(); String token = bearer(account);
        for (Role role : Role.values()) {
            jdbc.update("UPDATE users SET role=?,status='review' WHERE id=?", role.name(), id);
            var principal = authentication.authenticate(token);
            assertThat(principal.role()).isEqualTo(role.name());
            assertThat(principal.status()).isEqualTo("review");
            Role.requireAny(principal, role);
            Role other = role == Role.USER ? Role.MODERATOR : Role.USER;
            assertThatThrownBy(() -> Role.requireAny(principal, other)).hasMessage("You do not have permission to perform this action");
            assertThat(request("GET", "/me", null, token).body.path("user").path("role").asText()).isEqualTo(role.name());
        }
        jdbc.update("UPDATE users SET status='suspended' WHERE id=?", id);
        error(request("GET", "/me", null, token), 403, "This account has been suspended");
        error(request("POST", "/login", Map.of("email", account.path("user").path("email").asText(), "password", "wrong"), null), 401, "Invalid email or password");
        error(request("POST", "/login", Map.of("email", account.path("user").path("email").asText(), "password", "Password123!"), null), 403, "This account has been suspended");
        jdbc.update("DELETE FROM users WHERE id=?", id);
        error(request("GET", "/me", null, token), 401, "Account no longer exists");
    }
    @Test void bearerParsingAndPublicEndpointsMatchExpress() throws Exception {
        var account = register();
        for (String header : Arrays.asList(null, "Basic abc", "bearer abc", "Bearer", "Bearer  abc")) error(request("GET", "/me", null, header), 401, "Unauthorized");
        error(request("GET", "/me", null, "Bearer invalid"), 401, "Invalid or expired token");
        assertThat(request("GET", "/me", null, bearer(account) + " ignored").status).isEqualTo(200);
        error(request("POST", "/login", Map.of(), "Bearer invalid"), 400, "Email is required");
        error(request("POST", "/login", Map.of("email", "a"), null), 400, "Password is required");
        error(request("POST", "/login", Map.of("email", "missing", "password", "wrong"), null), 401, "Invalid email or password");
        error(request("GET", "/me", null, null), 401, "Unauthorized");
    }
    @Test void registrationRollsBackUserWhenWalletInsertFails() throws Exception {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        jdbc.execute("CREATE TRIGGER fail_wallet BEFORE INSERT ON wallets BEGIN SELECT RAISE(ABORT, 'test wallet failure'); END");
        try {
            var response = request("POST", "/register", Map.of("fullName", "Rollback", "email", "rollback@example.test", "password", "Password123!"), null);
            assertThat(response.status).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(count);
        } finally { jdbc.execute("DROP TRIGGER fail_wallet"); }
    }
    @Test void actualLegacyTokenAndStoredHashWorkWithoutRehashing() throws Exception {
        JsonNode f;
        try (var input = getClass().getResourceAsStream("/auth/legacy-auth.json")) { f = mapper.readTree(input); }
        String hash = f.path("passwords").get(0).path("hash").asText();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,role,status) VALUES (9001,'Legacy','legacy@example.test',?,'USER','review')", hash);
        try {
            var me = request("GET", "/me", null, "Bearer " + f.path("tokens").path("HS256").asText());
            assertThat(me.status).isEqualTo(200); assertThat(me.body.path("user").path("role").asText()).isEqualTo("USER");
            assertThat(me.body.path("user").path("email").asText()).isEqualTo("legacy@example.test");
            assertThat(request("POST", "/login", Map.of("email", "legacy@example.test", "password", f.path("passwords").get(0).path("password").asText()), null).status).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=9001", String.class)).isEqualTo(hash);
            error(request("GET", "/me", null, "Bearer " + f.path("tokens").path("withoutId").asText()), 401, "Account no longer exists");
        } finally { jdbc.update("DELETE FROM users WHERE id=9001"); }
    }
    @Test void logoutRevokesOnlyExactTokenVaultGrantsAndDoesNotRevokeJwt() throws Exception {
        var account = register(); long id = account.path("user").path("id").asLong();
        var login = request("POST", "/login", Map.of("email", account.path("user").path("email").asText(), "password", "Password123!"), null);
        String first = JwtService.fingerprint(account.path("token").asText());
        String second = JwtService.fingerprint(login.body.path("token").asText());
        for (int i = 0; i < 2; i++) {
            String reference = "VT-" + UUID.randomUUID();
            jdbc.update("INSERT INTO vaults(user_id,public_reference,name) VALUES (?,?,'Test')", id, reference);
            long vault = jdbc.queryForObject("SELECT id FROM vaults WHERE public_reference=?", Long.class, reference);
            for (String fingerprint : List.of(first, second)) jdbc.update("INSERT INTO vault_unlock_sessions(vault_id,user_id,token_fingerprint,expires_at) VALUES (?,?,?,'2099-01-01')", vault, id, fingerprint);
        }
        var logout = request("POST", "/logout", null, bearer(account));
        assertThat(logout.status).isEqualTo(200); assertThat(logout.body.path("message").asText()).isEqualTo("Logged out successfully");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vault_unlock_sessions WHERE user_id=? AND token_fingerprint=?", Integer.class, id, first)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vault_unlock_sessions WHERE user_id=? AND token_fingerprint=?", Integer.class, id, second)).isEqualTo(2);
        assertThat(request("GET", "/me", null, bearer(account)).status).isEqualTo(200);
        assertThat(request("POST", "/logout", null, bearer(account)).status).isEqualTo(200);
    }
}
