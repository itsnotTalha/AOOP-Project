package com.authvault.service.impl;

import com.authvault.dto.auth.AuthResponse;
import com.authvault.dto.auth.LoginRequest;
import com.authvault.dto.auth.RegisterRequest;
import com.authvault.dto.user.UserResponse;
import com.authvault.entity.User;
import com.authvault.entity.VeriWallet;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.DuplicateResourceException;
import com.authvault.exception.UnauthorizedException;
import com.authvault.repository.UserRepository;
import com.authvault.repository.VeriWalletRepository;
import com.authvault.security.jwt.JwtService;
import com.authvault.service.AuthService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final VeriWalletRepository veriWalletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long tokenExpiration;

    public AuthServiceImpl(UserRepository userRepository,
                           VeriWalletRepository veriWalletRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           @Value("${jwt.expiration}") long tokenExpiration) {
        this.userRepository = userRepository;
        this.veriWalletRepository = veriWalletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already in use");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already in use");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Password and confirmation password do not match");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.USER);
        user.setPhone(request.getPhone());
        user.setVerified(false);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);

        VeriWallet wallet = new VeriWallet();
        wallet.setUser(savedUser);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setTotalEarned(BigDecimal.ZERO);
        wallet.setTotalSpent(BigDecimal.ZERO);
        wallet.setUpdatedAt(now);
        veriWalletRepository.save(wallet);

        return createAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getEmailOrUsername())
                .or(() -> userRepository.findByEmail(request.getEmailOrUsername()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(User user) {
        String token = jwtService.generateAccessToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .tokenType(TOKEN_TYPE)
                .expiresIn(tokenExpiration)
                .user(toUserResponse(user))
                .build();
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
