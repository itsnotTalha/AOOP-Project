package com.authvault.dto.asset;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetVerificationResponse {

    private String assetId;
    private String originalHash;
    private String currentHash;
    private boolean hashMatches;
    private String verificationStatus;
    private LocalDateTime verifiedAt;
}
