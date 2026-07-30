package com.verivault.controller;

import com.verivault.dto.common.ApiResponse;
import com.verivault.dto.user.UserResponse;
import com.verivault.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse userResponse = userService.getCurrentUser();
        return ResponseEntity.ok(successResponse("Current user retrieved successfully", userResponse));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @RequestBody UserService.UserProfileUpdateRequest request) {
        UserResponse userResponse = userService.updateCurrentUser(request);
        return ResponseEntity.ok(successResponse("Profile updated successfully", userResponse));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<UserResponse>> changeCurrentPassword(
            @Valid @RequestBody UserService.UserPasswordChangeRequest request) {
        UserResponse userResponse = userService.changeCurrentPassword(request);
        return ResponseEntity.ok(successResponse("Password changed successfully", userResponse));
    }

    private ApiResponse<UserResponse> successResponse(String message, UserResponse data) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}