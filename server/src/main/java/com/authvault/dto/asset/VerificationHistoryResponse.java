package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationHistoryResponse {

    private String verificationMethod;
    private String result;
    private String notes;
    private LocalDateTime verifiedAt;
}
