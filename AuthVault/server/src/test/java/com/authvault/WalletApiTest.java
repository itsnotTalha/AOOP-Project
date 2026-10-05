package com.authvault;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
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
class WalletApiTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("authvault.database-path",()->directory.resolve("wallet.sqlite").toString());}
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Test void depositWithdrawalValidationAndHistory() throws Exception {
        String body=json.writeValueAsString(Map.of("fullName","Wallet User","email",UUID.randomUUID()+"@test.invalid","password","Password123!"));
        String token="Bearer "+json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("token").asText();
        mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wallet").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.wallet.balance").value(0));
        mvc.perform(post("/api/wallet/transactions").header("Authorization",token).contentType("application/json")
            .content("{\"type\":\"sale\",\"amount\":100}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/wallet/transactions").header("Authorization",token).contentType("application/json")
            .content("{\"type\":\"deposit\",\"amount\":100}")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.wallet.balance").value(100));
        mvc.perform(post("/api/wallet/transactions").header("Authorization",token).contentType("application/json")
            .content("{\"type\":\"withdrawal\",\"amount\":101}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wallet/transactions").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.transactions.length()").value(1));
        mvc.perform(get("/api/dashboard/summary").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.walletBalance").value(100))
            .andExpect(jsonPath("$.summary.totalDocuments").value(0));
    }
}
