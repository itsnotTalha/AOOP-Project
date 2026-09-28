package com.authvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
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
class AssetApiTest {
 @TempDir static Path directory;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
 registry.add("authvault.database-path",()->directory.resolve("asset.sqlite").toString());
 registry.add("authvault.asset-directory",()->directory.resolve("uploads").toString());}
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;
 private JsonNode register() throws Exception {String body="{\"fullName\":\"Asset User\",\"email\":\""+UUID.randomUUID()+"@example.test\",\"password\":\"Password123!\"}";
 return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 private MockMultipartFile image() throws Exception {return new MockMultipartFile("file","synthetic-rgba.png","image/png",getClass().getResourceAsStream("/image/synthetic-rgba.png"));}
 @Test void uploadReadCheckAndOwnershipProtection() throws Exception {
 JsonNode owner=register(),other=register();String token="Bearer "+owner.path("token").asText();String otherToken="Bearer "+other.path("token").asText();
 mvc.perform(get("/api/assets")).andExpect(status().isUnauthorized());
 JsonNode uploaded=json.readTree(mvc.perform(multipart("/api/assets/upload").file(image()).param("title","Artwork").param("category","art")
 .header("Authorization",token)).andExpect(status().isCreated()).andExpect(jsonPath("$.asset.width").value(37))
 .andExpect(jsonPath("$.hash.phash").value("010113272f6f4e7f1ebeff917c222024444c18b8b1b9737bf7f4efe0e8a00121"))
 .andReturn().getResponse().getContentAsString());
 String id=uploaded.path("asset").path("id").asText();
 mvc.perform(get("/api/assets").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.assets.length()").value(1));
 mvc.perform(get("/api/assets/"+id).header("Authorization",otherToken)).andExpect(status().isNotFound());
 mvc.perform(get("/api/assets/"+id+"/metadata").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.metadata.pixelCount").value(1073));
 mvc.perform(get("/api/assets/"+id+"/hash").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.hashes.sha256").exists());
 mvc.perform(get("/api/assets/"+id+"/content").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, max-age=300"));
 mvc.perform(get("/api/assets/"+id+"/ownership-history").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(0));
 mvc.perform(multipart("/api/assets/check").file(image()).header("Authorization",otherToken)).andExpect(status().isOk())
 .andExpect(jsonPath("$.result.matchType").value("exact")).andExpect(jsonPath("$.result.asset.id").doesNotExist())
 .andExpect(jsonPath("$.result.asset.owner.isCurrentUser").value(false));
 mvc.perform(multipart("/api/assets/upload").file(image()).param("title","Again").param("category","art").header("Authorization",token))
 .andExpect(status().isConflict());
 assertThat(Files.list(directory.resolve("uploads")).count()).isEqualTo(1);
 }
}
