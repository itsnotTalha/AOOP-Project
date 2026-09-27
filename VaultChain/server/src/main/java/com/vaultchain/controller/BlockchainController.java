package com.vaultchain.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vaultchain.service.BlockchainService;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {
    private final BlockchainService blockchainService;

    public BlockchainController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    @GetMapping("/blocks")
    public List<Map<String, Object>> getBlocks(@RequestParam(defaultValue = "50") int limit) {
        return blockchainService.getBlocks(limit);
    }

    @GetMapping("/blocks/{index}")
    public Map<String, Object> getBlockByIndex(@PathVariable long index) {
        return blockchainService.getBlockByIndex(index);
    }

    @GetMapping("/asset/{assetId}")
    public List<Map<String, Object>> getBlocksForAsset(@PathVariable Long assetId) {
        return blockchainService.getBlocksForAsset(assetId);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return blockchainService.getStats();
    }

    @GetMapping("/flow/{assetId}")
    public Map<String, Object> getFlow(@PathVariable Long assetId, @RequestParam(required = false) String hash) {
        return blockchainService.getVerificationFlow(assetId, hash);
    }
}
