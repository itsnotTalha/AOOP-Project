package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetAiAnalysisResponse {

    private String assetId;
    private AiGenerationAnalysis aiGenerationAnalysis;
    private ManipulationAnalysis manipulationAnalysis;
    private LocalDateTime analyzedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiGenerationAnalysis {
        private boolean performed;
        private String status;
        private String modelName;
        private String modelVersion;
        private Double rawSyntheticScore;
        private Double decisionThreshold;
        private String modelSignal;
        private String calibrationStatus;
        private Double calibratedSyntheticProbability;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManipulationAnalysis {
        private boolean performed;
        private String status;
        private String modelName;
        private String modelVersion;
        private Double manipulationScore;
        private Double decisionThreshold;
        private String modelSignal;
        private boolean localizationAvailable;
        private Double suspiciousAreaRatio;
        private Double reliableSuspiciousAreaRatio;
        private String anomalyMapPngBase64;
        private String reliabilityMapPngBase64;
        private String suspiciousMaskPngBase64;
    }
}
