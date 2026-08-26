package com.authvault.controller;

import com.authvault.dto.originality.OriginalityOutcome;
import com.authvault.dto.originality.OriginalityReasonCode;
import com.authvault.dto.originality.OriginalityVerificationResponse;
import com.authvault.exception.GlobalExceptionHandler;
import com.authvault.service.OriginalityVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OriginalityVerificationControllerTest {

    @Mock
    private OriginalityVerificationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OriginalityVerificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsImageMultipartPartAndUsesExistingSuccessEnvelope() throws Exception {
        OriginalityVerificationResponse result = new OriginalityVerificationResponse(
                OriginalityOutcome.NO_REGISTERED_SOURCE_FOUND,
                new OriginalityVerificationResponse.Submitted("a".repeat(64), "0".repeat(16)),
                new OriginalityVerificationResponse.BlockchainLookup("COMPLETED", false),
                null,
                null,
                null,
                new OriginalityVerificationResponse.AiEvidence("DEFERRED"),
                List.of(
                        OriginalityReasonCode.BLOCKCHAIN_EXACT_MATCH_NOT_FOUND,
                        OriginalityReasonCode.NO_PLAUSIBLE_REGISTERED_SOURCE),
                LocalDateTime.of(2026, 8, 15, 12, 0));
        when(service.verify(any())).thenReturn(result);
        MockMultipartFile image = new MockMultipartFile(
                "image", "check.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/originality/verify").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.outcome")
                        .value("NO_REGISTERED_SOURCE_FOUND"))
                .andExpect(jsonPath("$.data.blockchainLookup.status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data.reasonCodes[1]")
                        .value("NO_PLAUSIBLE_REGISTERED_SOURCE"));
    }
}
