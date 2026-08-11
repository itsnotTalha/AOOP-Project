package com.authvault.service.impl;

import com.authvault.dto.user.UserResponse;
import com.authvault.entity.User;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.UserRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse getCurrentUser() {
        return toUserResponse(loadAuthenticatedUser());
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(UserProfileUpdateRequest request) {
        User user = loadAuthenticatedUser();

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }

        if (request.phone() != null) {
            user.setPhone(request.phone());
        }

        if (request.profileImage() != null) {
            user.setProfileImage(request.profileImage());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        return toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse changeCurrentPassword(UserPasswordChangeRequest request) {
        User user = loadAuthenticatedUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new com.authvault.exception.BadRequestException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new com.authvault.exception.BadRequestException("New password and confirm password do not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    private User loadAuthenticatedUser() {
        User authenticatedUser = SecurityUtils.getCurrentUser();

        return userRepository.findByUuid(authenticatedUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .uuid(user.getUuid())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .verified(user.isVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}