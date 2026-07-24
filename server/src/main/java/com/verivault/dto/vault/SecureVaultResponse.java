package com.verivault.dto.vault;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureVaultResponse {

    private Long id;
    private String title;
    private String vaultType;
    private LocalDateTime createdAt;
}
