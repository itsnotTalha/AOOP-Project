package com.authvault.dto.ai;

public record AiHealthResponse(
        String status,
        String service,
        boolean modelsReady,
        Models models) {

    public record Models(ModelState aiGeneration, ModelState manipulation) {
    }

    public record ModelState(
            boolean configured,
            boolean ready,
            String modelName,
            String modelVersion) {
    }
}
