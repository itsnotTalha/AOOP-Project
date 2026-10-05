package com.authvault.controller;

import com.authvault.security.CurrentUser;
import com.authvault.service.VerificationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verifications")
public class VerificationController {
    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user, @RequestPart(value = "file", required = false) MultipartFile file) {
        return Map.of("success", true, "verification", service.create(user.id(), user.tokenFingerprint(), file));
    }

    @GetMapping({"", "/"})
    public Map<String, Object> list(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("success", true, "verifications", service.list(user.id(), user.tokenFingerprint()));
    }

    @GetMapping("/{reference}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String reference) {
        return Map.of("success", true, "verification", service.get(user.id(), reference, user.tokenFingerprint()));
    }

    @GetMapping("/{reference}/matches/{assetId}/content")
    public ResponseEntity<byte[]> matchContent(@AuthenticationPrincipal CurrentUser user, @PathVariable String reference, @PathVariable Long assetId) {
        return service.matchContent(user.id(), reference, assetId, user.tokenFingerprint());
    }

    @PostMapping("/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDispute(@AuthenticationPrincipal CurrentUser user, @RequestBody Map<String, Object> body) {
        return Map.of("success", true, "dispute", service.createDispute(user.id(), body));
    }

    @GetMapping("/disputes")
    public Map<String, Object> listDisputes(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("success", true, "disputes", service.listDisputes(user.id()));
    }
}
