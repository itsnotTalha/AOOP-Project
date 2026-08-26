package com.authvault.controller;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.authvault.exception.BlockchainException;
import com.authvault.exception.GlobalExceptionHandler;
import com.authvault.service.BlockchainRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BlockchainRegistryControllerTest {

    private static final String ASSET_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private BlockchainRegistryService service;

    private MockMvc mockMvc;
    private BlockchainOriginalResponse original;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BlockchainRegistryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        original = new BlockchainOriginalResponse(
                ASSET_ID,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                SHA256,
                "IMAGE",
                "VERIFIED",
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                Instant.parse("2026-08-15T05:22:33Z"));
    }

    @Test
    void exposesRegistrationEndpointWithCreatedStatusAndTransactionId() throws Exception {
        when(service.registerOwnedAsset(ASSET_ID))
                .thenReturn(new BlockchainRegistrationResponse("tx-123", original));

        mockMvc.perform(post("/api/v1/assets/{assetId}/blockchain-registration", ASSET_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").value("tx-123"))
                .andExpect(jsonPath("$.data.asset.assetId").value(ASSET_ID));
    }

    @Test
    void exposesOwnerRegistrationAndHistoryQueries() throws Exception {
        when(service.getOwnedAssetRegistration(ASSET_ID)).thenReturn(original);
        BlockchainHistoryResponse history = new BlockchainHistoryResponse(
                "tx-123", Instant.parse("2026-08-15T05:22:33Z"), false, original);
        when(service.getOwnedAssetHistory(ASSET_ID)).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/assets/{assetId}/blockchain-registration", ASSET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sha256").value(SHA256));
        mockMvc.perform(get("/api/v1/assets/{assetId}/blockchain-history", ASSET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].transactionId").value("tx-123"));
    }

    @Test
    void exposesExactFabricShaLookup() throws Exception {
        when(service.findBySha256(SHA256))
                .thenReturn(new BlockchainOriginalLookupResponse(true, original));

        mockMvc.perform(get("/api/v1/blockchain/originals/by-sha256/{sha256}", SHA256))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.asset.assetId").value(ASSET_ID));
    }

    @Test
    void usesExistingErrorEnvelopeForFabricFailures() throws Exception {
        when(service.registerOwnedAsset(ASSET_ID)).thenThrow(new BlockchainException(
                "BLOCKCHAIN_DUPLICATE_ASSET",
                HttpStatus.CONFLICT,
                "The asset is already registered on the blockchain"));

        mockMvc.perform(post("/api/v1/assets/{assetId}/blockchain-registration", ASSET_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BLOCKCHAIN_DUPLICATE_ASSET"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/assets/" + ASSET_ID + "/blockchain-registration"));
    }
}
