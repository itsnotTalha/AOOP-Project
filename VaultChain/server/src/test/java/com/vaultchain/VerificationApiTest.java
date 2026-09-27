package com.vaultchain;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest @AutoConfigureMockMvc
class VerificationApiTest {
 @TempDir static Path directory;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("vaultchain.database-path",()->directory.resolve("verifications.sqlite").toString());
 r.add("vaultchain.asset-directory",()->directory.resolve("uploads").toString());}
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;
 private JsonNode register() throws Exception {String body="{\"fullName\":\"Verify User\",\"email\":\""+UUID.randomUUID()+"@example.test\",\"password\":\"Password123!\"}";
 return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 private MockMultipartFile image() throws Exception{return new MockMultipartFile("file","synthetic-rgba.png","image/png",getClass().getResourceAsStream("/image/synthetic-rgba.png"));}
 @Test void persistedReportsAreOwnerScoped() throws Exception {String token="Bearer "+register().path("token").asText();String other="Bearer "+register().path("token").asText();
 mvc.perform(get("/api/verifications")).andExpect(status().isUnauthorized());
 mvc.perform(multipart("/api/assets/upload").file(image()).param("title","Artwork").param("category","art").header("Authorization",token)).andExpect(status().isCreated());
 JsonNode created=json.readTree(mvc.perform(multipart("/api/verifications").file(image()).header("Authorization",other))
 .andExpect(status().isCreated()).andExpect(jsonPath("$.verification.result").value("matches_found"))
 .andExpect(jsonPath("$.verification.matches[0].ownerIsCurrentUser").value(false)).andReturn().getResponse().getContentAsString());
 String ref=created.path("verification").path("reference").asText();
 mvc.perform(get("/api/verifications").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.verifications.length()").value(1));
 mvc.perform(get("/api/verifications/"+ref).header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.verification.comparison.fileName").value("synthetic-rgba.png"));
 mvc.perform(get("/api/verifications/"+ref).header("Authorization",token)).andExpect(status().isNotFound());
 }
}
