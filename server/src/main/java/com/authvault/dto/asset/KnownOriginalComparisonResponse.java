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
public class KnownOriginalComparisonResponse {

    private String assetId;
    private boolean candidateFound;
    private KnownOriginalCandidateResponse candidate;
    private boolean comparisonPerformed;
    private String reason;
    private KnownOriginalComparisonResult comparison;
    private LocalDateTime analyzedAt;
}
