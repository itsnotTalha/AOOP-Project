package com.authvault.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.authvault.security.CurrentUser;
import com.authvault.service.AssetService;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Asset uploaded successfully");
        result.putAll(service.upload(user.id(), title, description, category, file));
        return result;
    }

    @PostMapping("/check")
    public Map<String, Object> check(
            @AuthenticationPrincipal CurrentUser user,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return Map.of("success", true, "result", service.check(user.id(), file));
    }

    @GetMapping({"", "/"})
    public Map<String, Object> list(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("success", true, "assets", service.list(user.id(), user.tokenFingerprint()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return Map.of("success", true, "asset", service.get(user.id(), id, user.tokenFingerprint()));
    }

    @GetMapping("/{id}/metadata")
    public Map<String, Object> metadata(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return Map.of("success", true, "metadata", service.metadata(user.id(), id, user.tokenFingerprint()));
    }

    @GetMapping("/{id}/hash")
    public Map<String, Object> hash(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return Map.of("success", true, "hashes", service.hash(user.id(), id, user.tokenFingerprint()));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return service.content(user.id(), id, user.tokenFingerprint());
    }

    @GetMapping("/{id}/ownership-history")
    public Map<String, Object> history(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return Map.of("success", true, "history", service.history(user.id(), id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return service.delete(user.id(), id, user.tokenFingerprint());
    }
}
