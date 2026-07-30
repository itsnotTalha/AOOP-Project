package com.verivault.service;

import com.verivault.dto.user.UserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public interface UserService {

    UserResponse getCurrentUser();

    UserResponse updateCurrentUser(UserProfileUpdateRequest request);

    UserResponse changeCurrentPassword(UserPasswordChangeRequest request);

    record UserProfileUpdateRequest(String fullName, String phone, String profileImage) {
    }

    record UserPasswordChangeRequest(
            @NotBlank(message = "Current password is required.") String currentPassword,
            @NotBlank(message = "New password is required.")
            @Size(min = 8, message = "New password must be at least 8 characters.") String newPassword,
            @NotBlank(message = "Confirm password is required.")
            @Size(min = 8, message = "Confirm password must be at least 8 characters.") String confirmPassword) {
    }
}