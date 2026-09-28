package com.authvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.authvault.entity.Asset;
import com.authvault.repository.AssetJpaRepository;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class VaultApiTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("authvault.database-path",()->directory.resolve("vault.sqlite").toString());
        r.add("authvault.vault-unlock-max-attempts",()->"3");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired AssetJpaRepository assets;
    private JsonNode register() throws Exception {
        String body="{\"fullName\":\"Vault User\",\"email\":\""+UUID.randomUUID()+"@example.test\",\"password\":\"Password123!\"}";
        return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private static String bearer(JsonNode response){return "Bearer "+response.path("token").asText();}
    @Test void anUnlockedVaultDoesNotRevealAssetProtectedByAnotherLockedVault() throws Exception {
        JsonNode owner=register();String token=bearer(owner);long userId=owner.path("user").path("id").asLong();
        Asset asset=new Asset();asset.setOwnerId(userId);asset.setTitle("Private image");asset.setCategory("art");
        asset.setFileName("private.png");asset.setFilePath("/tmp/private.png");asset.setStatus("active");asset=assets.saveAndFlush(asset);
        String[] refs=new String[2];
        for(int i=0;i<2;i++){
            JsonNode created=json.readTree(mvc.perform(post("/api/vaults").header("Authorization",token).contentType("application/json")
                .content("{\"name\":\"Vault "+i+"\",\"password\":\"VaultPass123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            refs[i]=created.path("vault").path("reference").asText();
            mvc.perform(post("/api/vaults/"+refs[i]+"/unlock").header("Authorization",token).contentType("application/json")
                .content("{\"password\":\"VaultPass123!\"}")).andExpect(status().isOk());
            mvc.perform(post("/api/vaults/"+refs[i]+"/assets").header("Authorization",token).contentType("application/json")
                .content("{\"assetIds\":["+asset.getId()+"]}")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/vaults/"+refs[1]+"/lock").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/vaults/"+refs[0]).header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.vault.assets[0].width").doesNotExist())
            .andExpect(jsonPath("$.vault.assets[0].contentUrl").doesNotExist())
            .andExpect(jsonPath("$.vault.assets[0].vaultProtection.isLocked").value(true));
        mvc.perform(get("/api/assets/"+asset.getId()+"/content").header("Authorization",token)).andExpect(status().is(423));
    }
    @Test void vaultLifecycleAndMembershipAreOwnerScoped() throws Exception {
        JsonNode owner=register(),other=register();String token=bearer(owner);
        mvc.perform(get("/api/vaults")).andExpect(status().isUnauthorized());
        JsonNode created=json.readTree(mvc.perform(post("/api/vaults").header("Authorization",token).contentType("application/json")
            .content("{\"name\":\" Private \",\"password\":\"VaultPass123!\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.vault.isLocked").value(true))
            .andReturn().getResponse().getContentAsString());
        String ref=created.path("vault").path("reference").asText();assertThat(ref).matches("VT-[A-F0-9]{6}");
        mvc.perform(get("/api/vaults/"+ref).header("Authorization",bearer(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/vaults/"+ref+"/unlock").header("Authorization",token).contentType("application/json")
            .content("{\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/vaults/"+ref+"/unlock").header("Authorization",token).contentType("application/json")
            .content("{\"password\":\"VaultPass123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.vault.isLocked").value(false));
        Asset asset=new Asset();asset.setOwnerId(owner.path("user").path("id").asLong());asset.setTitle("Artwork");
        asset.setFileName("test.png");asset.setFilePath("/tmp/test.png");asset.setCategory("art");asset.setStatus("active");
        asset=assets.saveAndFlush(asset);
        mvc.perform(post("/api/vaults/"+ref+"/assets").header("Authorization",token).contentType("application/json")
            .content("{\"assetIds\":["+asset.getId()+"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.vault.assetCount").value(1));
        mvc.perform(get("/api/vaults").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.stats.organizedAssets").value(1));
        mvc.perform(post("/api/vaults/"+ref+"/lock").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.vault.isLocked").value(true));
        mvc.perform(patch("/api/vaults/"+ref).header("Authorization",token).contentType("application/json")
            .content("{\"name\":\"New name\"}")).andExpect(status().is(423));
        mvc.perform(post("/api/vaults/"+ref+"/unlock").header("Authorization",token).contentType("application/json")
            .content("{\"password\":\"VaultPass123!\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/vaults/"+ref+"/assets/"+asset.getId()).header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.vault.assetCount").value(0));
        mvc.perform(delete("/api/vaults/"+ref).header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(assets.findById(asset.getId())).isPresent();
    }
}
