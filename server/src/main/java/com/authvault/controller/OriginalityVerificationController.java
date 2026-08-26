package com.authvault.controller;

import com.authvault.dto.common.ApiResponse;
import com.authvault.dto.originality.OriginalityVerificationResponse;
import com.authvault.service.OriginalityVerificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/originality")
public class OriginalityVerificationController {

    private final OriginalityVerificationService originalityVerificationService;

    public OriginalityVerificationController(
            OriginalityVerificationService originalityVerificationService) {
        this.originalityVerificationService = originalityVerificationService;
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OriginalityVerificationResponse>> verify(
            @RequestPart("image") MultipartFile image) {
        OriginalityVerificationResponse result =
                originalityVerificationService.verify(image);
        return ResponseEntity.ok(ApiResponse.<OriginalityVerificationResponse>builder()
                .success(true)
                .message("Originality verification completed")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
