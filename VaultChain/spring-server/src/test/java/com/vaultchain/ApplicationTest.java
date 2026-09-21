package com.vaultchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationTest {
    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // Overrides application.properties AND inherited DATABASE_PATH. Never use a real DB.
        registry.add("vaultchain.database-path", () -> directory.resolve("http.sqlite").toString());
    }

    @Autowired TestRestTemplate http;
    @Autowired DataSource source;
    @Autowired JdbcTemplate jdbc;
    @Autowired NamedParameterJdbcTemplate namedJdbc;

    @Test
    void startupInitializesTemporaryDatabaseAndProvidesJdbcTemplates() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", Integer.class))
                .isEqualTo(22);
        assertThat(namedJdbc.queryForObject("SELECT setting_value FROM platform_settings WHERE setting_key=:key",
                Map.of("key", "marketplace_commission_rate"), String.class)).isEqualTo("0.05");
        try (var connection = source.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains(directory.toString());
        }
    }

    @Test
    void healthMatchesLegacyExactlyIncludingTrailingSlash() {
        for (String path : new String[]{"/api/health", "/api/health/"}) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isEqualTo("{\"success\":true,\"message\":\"VaultChain API running\"}");
        }
    }

    @Test
    void unknownRoutesAndUnimplementedFeaturesReturnOnlyLegacyErrorFields() {
        for (String path : new String[]{"/api/missing", "/missing", "/api/auth/me", "/api/assets", "/error"}) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(404);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isEqualTo("{\"success\":false,\"message\":\"Route not found\"}");
        }
    }

    @Test
    void unsupportedHealthMethodFallsThroughLikeExpress() {
        var response = http.postForEntity("/api/health", null, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("{\"success\":false,\"message\":\"Route not found\"}");
    }

    @Test
    void browserHtmlAcceptStillGetsJsonForMissingRoute() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        var response = http.exchange("/api/missing", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"success\":false,\"message\":\"Route not found\"}");
    }

    @Test
    void corsAllowsViteOriginsAndAuthorizationHeader() {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.setAccessControlRequestMethod(HttpMethod.GET);
            headers.setAccessControlRequestHeaders(java.util.List.of("authorization", "content-type"));
            var response = http.exchange("/api/health", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH);
            assertThat(response.getHeaders().getAccessControlAllowHeaders()).contains("authorization", "content-type");
            assertThat(response.getHeaders().getAccessControlAllowCredentials()).isFalse();
            headers = new HttpHeaders();
            headers.setOrigin(origin);
            var actual = http.exchange("/api/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertThat(actual.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
        }
    }
}
