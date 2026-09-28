package com.authvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.authvault.entity.User;
import com.authvault.model.UserRecord;
import com.authvault.repository.UserJpaRepository;
import com.authvault.security.JwtService;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTest {
    @TempDir static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("authvault.database-path", () -> directory.resolve("admin.sqlite").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserJpaRepository users;
    @Autowired JwtService jwt;

    private String adminToken() throws Exception {
        String body = "{\"fullName\":\"Admin User\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"Password123!\"}";
        JsonNode res = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = res.path("user").path("id").asLong();
        User u = users.findById(id).orElseThrow();
        u.setRole("SUPER_ADMIN");
        users.saveAndFlush(u);
        return "Bearer " + jwt.sign(new UserRecord(u.getId(), u.getFullName(), u.getEmail(), u.getPasswordHash(), "SUPER_ADMIN", "active", null, null));
    }

    private String userToken() throws Exception {
        String body = "{\"fullName\":\"Normal User\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"Password123!\"}";
        JsonNode res = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return "Bearer " + res.path("token").asText();
    }

    @Test
    void normalUserForbiddenOnAdminRoutes() throws Exception {
        String token = userToken();
        mvc.perform(get("/api/admin/overview").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReadOverviewAndSettings() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/admin/overview").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totals.users").exists())
                .andExpect(jsonPath("$.overview.health").exists());

        mvc.perform(get("/api/admin/settings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings").isMap());
    }

    @Test
    void adminCanUpdateMarketplaceSettings() throws Exception {
        String token = adminToken();
        mvc.perform(patch("/api/admin/settings/marketplace").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commissionPercentage\":5.5,\"minimumListingPrice\":10.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplace.commissionPercentage").value(5.5))
                .andExpect(jsonPath("$.marketplace.minimumListingPrice").value(10.0));
    }

    @Test
    void adminCanUpdateUserRoleAndStatus() throws Exception {
        String admin = adminToken();
        String body = "{\"fullName\":\"Target User\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"password\":\"Password123!\"}";
        JsonNode res = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long targetId = res.path("user").path("id").asLong();

        mvc.perform(patch("/api/admin/users/" + targetId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MODERATOR\",\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("MODERATOR"));
    }

    @Test
    void adminNotificationsRead() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/admin/notifications").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray());

        mvc.perform(patch("/api/admin/notifications/read").header("Authorization", token))
                .andExpect(status().isNoContent());
    }
}
