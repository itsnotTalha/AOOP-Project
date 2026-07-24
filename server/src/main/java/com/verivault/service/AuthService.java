package com.verivault.service;

import com.verivault.dto.auth.AuthResponse;
import com.verivault.dto.auth.LoginRequest;
import com.verivault.dto.auth.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
