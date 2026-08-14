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
public class SimilarImageMatchResponse {

    private String matchedAssetId;
    private String title;
    private String originalFilename;
    private LocalDateTime uploadedAt;
    private int hammingDistance;
    private PerceptualMatchBand matchBand;
}
