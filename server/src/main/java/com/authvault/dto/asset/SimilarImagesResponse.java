package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarImagesResponse {

    private String assetId;
    private String perceptualHash;
    private List<SimilarImageMatchResponse> closestMatches;
}
