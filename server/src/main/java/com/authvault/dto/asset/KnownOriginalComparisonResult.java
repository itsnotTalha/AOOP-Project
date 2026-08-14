package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownOriginalComparisonResult {

    private String status;
    private Alignment alignment;
    private boolean differencePerformed;
    private Double changedAreaRatio;
    private Double meanAbsoluteDifference;
    private Double structuralSimilarity;
    private String changeMaskPngBase64;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alignment {
        private String status;
        private String method;
        private int keypointsReference;
        private int keypointsTarget;
        private int goodMatches;
        private int inliers;
        private Double inlierRatio;
    }
}
