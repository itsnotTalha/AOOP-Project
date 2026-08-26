package com.authvault.dto.comparison;

public record ImageComparisonResponse(
        String comparisonVersion,
        Alignment alignment,
        Difference difference,
        ChangeMask mask,
        String status) {

    public record Alignment(
            String status,
            String method,
            int keypointsReference,
            int keypointsTarget,
            int goodMatches,
            int inliers,
            Double inlierRatio) {
    }

    public record Difference(
            boolean performed,
            Double changedAreaRatio,
            Double meanAbsoluteDifference,
            Double structuralSimilarity) {
    }

    public record ChangeMask(
            boolean available,
            String format,
            String base64) {
    }
}
