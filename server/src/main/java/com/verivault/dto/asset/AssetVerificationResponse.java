package com.verivault.dto.asset;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetVerificationResponse {

    private String assetUuid;
    private String verificationResult;
    private BigDecimal similarityScore;
    private boolean sha256Matched;
    private boolean perceptualMatched;
    private boolean blockchainVerified;
}
