package com.authvault.service.impl;

import com.authvault.client.ai.AiClientResult;
import com.authvault.client.ai.AiForensicsClient;
import com.authvault.dto.ai.AiImageAnalysisResponse;
import com.authvault.dto.asset.AssetAiAnalysisResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.exception.VerificationException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AiAssetAnalysisService;
import com.authvault.service.AssetStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class AiAssetAnalysisServiceImpl implements AiAssetAnalysisService {

    private static final String AI_SERVICE_DISABLED = "AI_SERVICE_DISABLED";
    private static final String AI_SERVICE_UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";

    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetStorageService assetStorageService;
    private final AiForensicsClient aiForensicsClient;

    public AiAssetAnalysisServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            AssetStorageService assetStorageService,
            AiForensicsClient aiForensicsClient) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.assetStorageService = assetStorageService;
        this.aiForensicsClient = aiForensicsClient;
    }

    @Override
    public AssetAiAnalysisResponse analyzeOwnedImage(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        if (asset.getAssetType() != DigitalAsset.AssetType.IMAGE) {
            throw new BadRequestException("AI generation analysis is available only for image assets");
        }

        try (InputStream inputStream = assetStorageService.loadStoredAsset(asset.getStoragePath())) {
            AiClientResult<AiImageAnalysisResponse> result = aiForensicsClient.analyzeImage(
                    controlledResource(inputStream),
                    safeImageMediaType(asset.getMimeType()));
            return mapResult(asset.getUuid(), result);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VerificationException("Could not analyze image", exception);
        }
    }

    private AssetAiAnalysisResponse mapResult(
            String assetId,
            AiClientResult<AiImageAnalysisResponse> result) {
        return switch (result.status()) {
            case DISABLED -> unavailable(assetId, AI_SERVICE_DISABLED);
            case UNAVAILABLE -> unavailable(assetId, AI_SERVICE_UNAVAILABLE);
            case SUCCESS -> completed(assetId, result.body());
        };
    }

    private AssetAiAnalysisResponse completed(
            String assetId,
            AiImageAnalysisResponse internal) {
        if (internal == null) {
            return unavailable(assetId, AI_SERVICE_UNAVAILABLE);
        }
        return AssetAiAnalysisResponse.builder()
                .assetId(assetId)
                .aiGenerationAnalysis(mapAiGeneration(internal.aiGeneration()))
                .manipulationAnalysis(mapManipulation(internal.manipulation()))
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private AssetAiAnalysisResponse unavailable(String assetId, String status) {
        return AssetAiAnalysisResponse.builder()
                .assetId(assetId)
                .aiGenerationAnalysis(unavailableAiGeneration(status))
                .manipulationAnalysis(unavailableManipulation(status))
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private AssetAiAnalysisResponse.AiGenerationAnalysis mapAiGeneration(
            AiImageAnalysisResponse.AiGenerationAnalysis analysis) {
        if (analysis == null) {
            return unavailableAiGeneration(AI_SERVICE_UNAVAILABLE);
        }
        return AssetAiAnalysisResponse.AiGenerationAnalysis.builder()
                .performed(analysis.performed())
                .status(analysis.status())
                .modelName(analysis.modelName())
                .modelVersion(analysis.modelVersion())
                .rawSyntheticScore(analysis.rawSyntheticScore())
                .decisionThreshold(analysis.decisionThreshold())
                .modelSignal(analysis.modelSignal())
                .calibrationStatus(analysis.calibrationStatus())
                .calibratedSyntheticProbability(analysis.calibratedSyntheticProbability())
                .build();
    }

    private AssetAiAnalysisResponse.ManipulationAnalysis mapManipulation(
            AiImageAnalysisResponse.ManipulationAnalysis analysis) {
        if (analysis == null) {
            return unavailableManipulation(AI_SERVICE_UNAVAILABLE);
        }
        return AssetAiAnalysisResponse.ManipulationAnalysis.builder()
                .performed(analysis.performed())
                .status(analysis.status())
                .modelName(analysis.modelName())
                .modelVersion(analysis.modelVersion())
                .manipulationScore(analysis.manipulationScore())
                .decisionThreshold(analysis.decisionThreshold())
                .modelSignal(analysis.modelSignal())
                .localizationAvailable(analysis.localizationAvailable())
                .suspiciousAreaRatio(analysis.suspiciousAreaRatio())
                .reliableSuspiciousAreaRatio(analysis.reliableSuspiciousAreaRatio())
                .anomalyMapPngBase64(analysis.anomalyMapPngBase64())
                .reliabilityMapPngBase64(analysis.reliabilityMapPngBase64())
                .suspiciousMaskPngBase64(analysis.suspiciousMaskPngBase64())
                .build();
    }

    private AssetAiAnalysisResponse.AiGenerationAnalysis unavailableAiGeneration(String status) {
        return AssetAiAnalysisResponse.AiGenerationAnalysis.builder()
                .performed(false)
                .status(status)
                .build();
    }

    private AssetAiAnalysisResponse.ManipulationAnalysis unavailableManipulation(String status) {
        return AssetAiAnalysisResponse.ManipulationAnalysis.builder()
                .performed(false)
                .status(status)
                .localizationAvailable(false)
                .build();
    }

    private Resource controlledResource(InputStream inputStream) {
        return new InputStreamResource(inputStream) {
            @Override
            public String getFilename() {
                return "analysis-image";
            }

            @Override
            public long contentLength() {
                return -1L;
            }
        };
    }

    private MediaType safeImageMediaType(String mimeType) {
        try {
            MediaType parsed = MediaType.parseMediaType(mimeType);
            if (MediaType.IMAGE_JPEG.equals(parsed) || MediaType.IMAGE_PNG.equals(parsed)) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // Actual bytes are validated by the AI service.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
