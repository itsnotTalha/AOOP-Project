package com.authvault.controller;

import com.authvault.dto.common.ApiResponse;
import com.authvault.dto.verification.AuthenticatorReviewDetailResponse;
import com.authvault.dto.verification.AuthenticatorReviewRequest;
import com.authvault.dto.verification.AuthenticatorReviewResponse;
import com.authvault.dto.verification.PendingVerificationResponse;
import com.authvault.service.AuthenticatorReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/authenticator/reviews")
@PreAuthorize("hasAnyRole('AUTHENTICATOR','ADMIN')")
public class AuthenticatorReviewController {

    private final AuthenticatorReviewService reviewService;

    public AuthenticatorReviewController(AuthenticatorReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PendingVerificationResponse>>> pending() {
        return ResponseEntity.ok(response(
                "Pending verification reviews retrieved",
                reviewService.getPendingReviews()));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<ApiResponse<AuthenticatorReviewDetailResponse>> getReview(
            @PathVariable String assetId) {
        return ResponseEntity.ok(response(
                "Verification review retrieved",
                reviewService.getReview(assetId)));
    }

    @PostMapping("/{assetId}/approve")
    public ResponseEntity<ApiResponse<AuthenticatorReviewResponse>> approve(
            @PathVariable String assetId,
            @Valid @RequestBody(required = false) AuthenticatorReviewRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(response(
                "Asset verification approved",
                reviewService.approve(assetId, reason)));
    }

    @PostMapping("/{assetId}/reject")
    public ResponseEntity<ApiResponse<AuthenticatorReviewResponse>> reject(
            @PathVariable String assetId,
            @Valid @RequestBody(required = false) AuthenticatorReviewRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(response(
                "Asset verification rejected",
                reviewService.reject(assetId, reason)));
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
