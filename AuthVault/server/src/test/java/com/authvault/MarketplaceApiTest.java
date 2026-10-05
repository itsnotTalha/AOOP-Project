package com.authvault;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
@SpringBootTest @AutoConfigureMockMvc
class MarketplaceApiTest {
 @TempDir static Path directory;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("authvault.database-path",()->directory.resolve("market.sqlite").toString());
 r.add("authvault.asset-directory",()->directory.resolve("uploads").toString());}
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired DataSource database;
 private JsonNode register() throws Exception {String body="{\"fullName\":\"Market User\",\"email\":\""+UUID.randomUUID()+"@example.test\",\"password\":\"Password123!\"}";
 return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 private MockMultipartFile image() throws Exception{return new MockMultipartFile("file","synthetic-rgba.png","image/png",getClass().getResourceAsStream("/image/synthetic-rgba.png"));}
 @Test void listingSaleTransfersOwnershipAndBalances() throws Exception {String seller="Bearer "+register().path("token").asText(),buyer="Bearer "+register().path("token").asText();
 mvc.perform(get("/api/marketplace/listings")).andExpect(status().isUnauthorized());
 JsonNode uploaded=json.readTree(mvc.perform(multipart("/api/assets/upload").file(image()).param("title","Artwork").param("category","art").header("Authorization",seller))
 .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());long asset=uploaded.path("asset").path("id").asLong();
 JsonNode created=json.readTree(mvc.perform(post("/api/marketplace/listings").header("Authorization",seller).contentType("application/json")
 .content("{\"assetId\":"+asset+",\"title\":\"Artwork\",\"price\":100}"))
 .andExpect(status().isCreated()).andExpect(jsonPath("$.listing.asset.previewAvailable").value(true)).andReturn().getResponse().getContentAsString());
 String ref=created.path("listing").path("reference").asText();assertThat(ref).matches("ML-[A-F0-9]{6}");
 mvc.perform(get("/api/marketplace/listings").header("Authorization",buyer)).andExpect(status().isOk()).andExpect(jsonPath("$.listings.length()").value(1));
 mvc.perform(get("/api/marketplace/listings/"+ref+"/content").header("Authorization",buyer)).andExpect(status().isForbidden());
 long previewId=json.readTree(mvc.perform(post("/api/marketplace/listings/"+ref+"/preview-requests").header("Authorization",buyer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("request").path("id").asLong();
 mvc.perform(patch("/api/marketplace/listings/"+ref+"/preview-requests/"+previewId).header("Authorization",seller).contentType("application/json").content("{\"status\":\"approved\"}")).andExpect(status().isOk());
 mvc.perform(get("/api/marketplace/listings/"+ref+"/content").header("Authorization",buyer)).andExpect(status().isOk());
 mvc.perform(patch("/api/marketplace/listings/"+ref).header("Authorization",buyer).contentType("application/json").content("{\"price\":50}"))
 .andExpect(status().isNotFound());
 mvc.perform(patch("/api/marketplace/listings/"+ref).header("Authorization",seller).contentType("application/json").content("{\"price\":120}"))
 .andExpect(status().isOk()).andExpect(jsonPath("$.listing.price").value(120));
 mvc.perform(post("/api/wallet/transactions").header("Authorization",buyer).contentType("application/json").content("{\"type\":\"deposit\",\"amount\":200}"))
 .andExpect(status().isCreated());
 JdbcTemplate sql=new JdbcTemplate(database);
 sql.execute("CREATE TRIGGER fail_purchase_history BEFORE INSERT ON ownership_history BEGIN SELECT RAISE(ABORT, 'history unavailable'); END");
 mvc.perform(post("/api/marketplace/listings/"+ref+"/purchase").header("Authorization",buyer)).andExpect(status().is5xxServerError());
 assertThat(sql.queryForObject("SELECT balance FROM wallets WHERE user_id=(SELECT id FROM users WHERE email=?)",Double.class,
 json.readTree(mvc.perform(get("/api/auth/me").header("Authorization",buyer)).andReturn().getResponse().getContentAsString()).path("user").path("email").asText())).isEqualTo(200.0);
 assertThat(sql.queryForObject("SELECT status FROM marketplace_listings WHERE public_reference=?",String.class,ref)).isEqualTo("active");
 assertThat(sql.queryForObject("SELECT COUNT(*) FROM ownership_history",Integer.class)).isZero();
 sql.execute("DROP TRIGGER fail_purchase_history");
 mvc.perform(post("/api/marketplace/listings/"+ref+"/purchase").header("Authorization",buyer))
 .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.buyerBalance").value(80));
 mvc.perform(post("/api/marketplace/listings/"+ref+"/purchase").header("Authorization",buyer)).andExpect(status().isConflict());
 mvc.perform(get("/api/assets/"+asset).header("Authorization",seller)).andExpect(status().isNotFound());
 mvc.perform(get("/api/assets/"+asset).header("Authorization",buyer)).andExpect(status().isOk());
 mvc.perform(get("/api/assets/"+asset+"/ownership-history").header("Authorization",buyer)).andExpect(status().isOk())
 .andExpect(jsonPath("$.history[0].listingReference").value(ref));
 }
}
