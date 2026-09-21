package com.vaultchain.controller;

import com.vaultchain.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping({"/api/health", "/api/health/"})
    public HealthResponse health() {
        return new HealthResponse(true, "VaultChain API running");
    }
}
