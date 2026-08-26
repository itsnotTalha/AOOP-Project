package com.authvault.controller;

import com.authvault.dto.common.ApiResponse;
import com.authvault.dto.verification.VerificationEvidenceResponse;
import com.authvault.service.VerificationEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets/{assetId}/verification/evidence")
public class VerificationEvidenceController {

    private final VerificationEvidenceService evidenceService;

    public VerificationEvidenceController(VerificationEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VerificationEvidenceResponse>> generate(
            @PathVariable String assetId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(
                "Verification evidence generated",
                evidenceService.generateEvidence(assetId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<VerificationEvidenceResponse>> getLatest(
            @PathVariable String assetId) {
        return ResponseEntity.ok(response(
                "Verification evidence retrieved",
                evidenceService.getLatestEvidence(assetId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<VerificationEvidenceResponse>>> getHistory(
            @PathVariable String assetId) {
        return ResponseEntity.ok(response(
                "Verification evidence history retrieved",
                evidenceService.getEvidenceHistory(assetId)));
    }

    private <T> ApiResponse<T> response(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
