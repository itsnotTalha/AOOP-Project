package com.authvault.service.impl;

import com.authvault.dto.auth.AuthResponse;
import com.authvault.dto.auth.RegisterRequest;
import com.authvault.entity.User;
import com.authvault.repository.UserRepository;
import com.authvault.repository.VeriWalletRepository;
import com.authvault.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VeriWalletRepository walletRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @Test
    void publicRegistrationAlwaysCreatesOrdinaryUser() {
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("new-user")).thenReturn("token");
        AuthServiceImpl service = new AuthServiceImpl(
                userRepository, walletRepository, passwordEncoder, jwtService, 3600L);
        RegisterRequest request = RegisterRequest.builder()
                .fullName("New User")
                .username("new-user")
                .email("new@example.com")
                .password("password123")
                .confirmPassword("password123")
                .build();

        AuthResponse response = service.register(request);

        assertThat(response.getUser().getRole()).isEqualTo("USER");
    }
}
