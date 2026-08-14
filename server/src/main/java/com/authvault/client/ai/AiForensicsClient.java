package com.authvault.client.ai;

import com.authvault.dto.ai.AiHealthResponse;
import com.authvault.dto.ai.AiImageAnalysisResponse;
import com.authvault.dto.ai.AiImageComparisonResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public interface AiForensicsClient {

    AiClientResult<AiHealthResponse> health();

    AiClientResult<AiImageAnalysisResponse> analyzeImage(
            Resource controlledImage,
            MediaType mediaType);

    AiClientResult<AiImageComparisonResponse> compareImages(
            Resource controlledReference,
            MediaType referenceMediaType,
            Resource controlledTarget,
            MediaType targetMediaType);
}
