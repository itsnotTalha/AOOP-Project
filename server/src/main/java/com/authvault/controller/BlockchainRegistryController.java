package com.authvault.controller;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalLookupResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.authvault.dto.common.ApiResponse;
import com.authvault.service.BlockchainRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class BlockchainRegistryController {

    private final BlockchainRegistryService blockchainRegistryService;

    public BlockchainRegistryController(BlockchainRegistryService blockchainRegistryService) {
        this.blockchainRegistryService = blockchainRegistryService;
    }

    @PostMapping("/api/v1/assets/{assetId}/blockchain-registration")
    public ResponseEntity<ApiResponse<BlockchainRegistrationResponse>> registerOriginal(
            @PathVariable String assetId) {
        BlockchainRegistrationResponse registration =
                blockchainRegistryService.registerOwnedAsset(assetId);
        return ResponseEntity.status(HttpStatus.CREATED).body(successResponse(
                "Asset registered on the blockchain", registration));
    }

    @GetMapping("/api/v1/assets/{assetId}/blockchain-registration")
    public ResponseEntity<ApiResponse<BlockchainOriginalResponse>> getRegistration(
            @PathVariable String assetId) {
        BlockchainOriginalResponse registration =
                blockchainRegistryService.getOwnedAssetRegistration(assetId);
        return ResponseEntity.ok(successResponse(
                "Blockchain registration retrieved successfully", registration));
    }

    @GetMapping("/api/v1/assets/{assetId}/blockchain-history")
    public ResponseEntity<ApiResponse<List<BlockchainHistoryResponse>>> getHistory(
            @PathVariable String assetId) {
        List<BlockchainHistoryResponse> history =
                blockchainRegistryService.getOwnedAssetHistory(assetId);
        return ResponseEntity.ok(successResponse(
                "Blockchain history retrieved successfully", history));
    }

    @GetMapping("/api/v1/blockchain/originals/by-sha256/{sha256}")
    public ResponseEntity<ApiResponse<BlockchainOriginalLookupResponse>> findBySha256(
            @PathVariable String sha256) {
        BlockchainOriginalLookupResponse lookup =
                blockchainRegistryService.findBySha256(sha256);
        return ResponseEntity.ok(successResponse(
                "Blockchain SHA-256 lookup completed", lookup));
    }

    private <T> ApiResponse<T> successResponse(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
