package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownOriginalCandidateResponse {

    private String assetId;
    private String title;
    private String originalFilename;
    private int hammingDistance;
    private PerceptualMatchBand matchBand;
}
