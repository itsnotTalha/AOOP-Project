package com.authvault.controller;

import com.authvault.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping({"/api/health", "/api/health/"})
    public HealthResponse health() {
        return new HealthResponse(true, "AuthVault API running");
    }
}
