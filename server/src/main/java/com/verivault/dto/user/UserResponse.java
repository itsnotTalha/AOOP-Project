package com.verivault.dto.user;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String uuid;
    private String fullName;
    private String username;
    private String email;
    private String profileImage;
    private String phone;
    private String role;
    private boolean verified;
    private LocalDateTime createdAt;
}
