package com.authvault.dto.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureVaultRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String vaultType;

    @NotBlank
    private String plainText;
}
