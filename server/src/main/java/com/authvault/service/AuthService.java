package com.authvault.service;

import com.authvault.dto.auth.AuthResponse;
import com.authvault.dto.auth.LoginRequest;
import com.authvault.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
