package com.authvault.dto.ai;

public record AiImageAnalysisResponse(
        String analysisVersion,
        AiGenerationAnalysis aiGeneration,
        ManipulationAnalysis manipulation) {

    public record AiGenerationAnalysis(
            boolean performed,
            String modelName,
            String modelVersion,
            Double rawLogit,
            Double rawSyntheticScore,
            Double decisionThreshold,
            String modelSignal,
            String calibrationStatus,
            Double calibratedSyntheticProbability,
            String status) {
    }

    public record ManipulationAnalysis(
            boolean performed,
            String modelName,
            String modelVersion,
            Double manipulationScore,
            Double decisionThreshold,
            String modelSignal,
            boolean localizationAvailable,
            Double suspiciousAreaRatio,
            Double reliableSuspiciousAreaRatio,
            String anomalyMapPngBase64,
            String reliabilityMapPngBase64,
            String suspiciousMaskPngBase64,
            String status) {
    }
}
