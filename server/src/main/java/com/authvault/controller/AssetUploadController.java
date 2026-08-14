package com.authvault.controller;

import com.authvault.dto.asset.AssetResponse;
import com.authvault.dto.asset.AssetDetailResponse;
import com.authvault.dto.asset.AssetUploadRequest;
import com.authvault.dto.asset.AssetVerificationResponse;
import com.authvault.dto.asset.AssetAiAnalysisResponse;
import com.authvault.dto.asset.VerificationHistoryResponse;
import com.authvault.dto.asset.SimilarImagesResponse;
import com.authvault.dto.asset.KnownOriginalComparisonResponse;
import com.authvault.dto.common.ApiResponse;
import com.authvault.service.AssetIntegrityService;
import com.authvault.service.AssetManagementService;
import com.authvault.service.AssetQueryService;
import com.authvault.service.AssetUploadService;
import com.authvault.service.AiAssetAnalysisService;
import com.authvault.service.KnownOriginalCandidateService;
import com.authvault.service.KnownOriginalComparisonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetUploadController {

    private final AssetUploadService assetUploadService;
    private final AssetIntegrityService assetIntegrityService;
    private final AssetQueryService assetQueryService;
    private final AssetManagementService assetManagementService;
    private final KnownOriginalCandidateService knownOriginalCandidateService;
    private final KnownOriginalComparisonService knownOriginalComparisonService;
    private final AiAssetAnalysisService aiAssetAnalysisService;

    public AssetUploadController(
            AssetUploadService assetUploadService,
            AssetIntegrityService assetIntegrityService,
            AssetQueryService assetQueryService,
            AssetManagementService assetManagementService,
            KnownOriginalCandidateService knownOriginalCandidateService,
            KnownOriginalComparisonService knownOriginalComparisonService,
            AiAssetAnalysisService aiAssetAnalysisService) {
        this.assetUploadService = assetUploadService;
        this.assetIntegrityService = assetIntegrityService;
        this.assetQueryService = assetQueryService;
        this.assetManagementService = assetManagementService;
        this.knownOriginalCandidateService = knownOriginalCandidateService;
        this.knownOriginalComparisonService = knownOriginalComparisonService;
        this.aiAssetAnalysisService = aiAssetAnalysisService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetResponse>> uploadImage(
            @Valid @ModelAttribute AssetUploadRequest request) {
        AssetResponse asset = assetUploadService.uploadImage(request);
        return created("Image uploaded successfully", asset);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetResponse>> uploadDocument(
            @Valid @ModelAttribute AssetUploadRequest request) {
        AssetResponse asset = assetUploadService.uploadDocument(request);
        return created("Document uploaded successfully", asset);
    }

    @PostMapping("/{assetId}/verify-integrity")
    public ResponseEntity<ApiResponse<AssetVerificationResponse>> verifyIntegrity(
            @PathVariable String assetId) {
        AssetVerificationResponse verification = assetIntegrityService.verifyIntegrity(assetId);
        return ResponseEntity.ok(successResponse(
                "Asset integrity verified successfully", verification));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponse>>> listAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        List<AssetResponse> assets = assetQueryService.listOwnedAssets(type, status, search, sort);
        return ResponseEntity.ok(successResponse("Assets retrieved successfully", assets));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<ApiResponse<AssetDetailResponse>> getAsset(
            @PathVariable String assetId) {
        AssetDetailResponse asset = assetQueryService.getOwnedAsset(assetId);
        return ResponseEntity.ok(successResponse("Asset retrieved successfully", asset));
    }

    @GetMapping("/{assetId}/verification-history")
    public ResponseEntity<ApiResponse<List<VerificationHistoryResponse>>> getVerificationHistory(
            @PathVariable String assetId) {
        List<VerificationHistoryResponse> history =
                assetQueryService.getOwnedAssetVerificationHistory(assetId);
        return ResponseEntity.ok(successResponse(
                "Verification history retrieved successfully", history));
    }

    @GetMapping("/{assetId}/similar-images")
    public ResponseEntity<ApiResponse<SimilarImagesResponse>> getSimilarImages(
            @PathVariable String assetId) {
        SimilarImagesResponse matches = knownOriginalCandidateService.findSimilarImages(assetId);
        return ResponseEntity.ok(successResponse(
                "Similar image candidates retrieved successfully", matches));
    }

    @PostMapping("/{assetId}/compare-known-original")
    public ResponseEntity<ApiResponse<KnownOriginalComparisonResponse>> compareKnownOriginal(
            @PathVariable String assetId) {
        KnownOriginalComparisonResponse comparison =
                knownOriginalComparisonService.compareWithKnownOriginal(assetId);
        return ResponseEntity.ok(successResponse(
                "Known-original comparison completed", comparison));
    }

    @PostMapping("/{assetId}/analyze-ai")
    public ResponseEntity<ApiResponse<AssetAiAnalysisResponse>> analyzeAi(
            @PathVariable String assetId) {
        AssetAiAnalysisResponse analysis = aiAssetAnalysisService.analyzeOwnedImage(assetId);
        return ResponseEntity.ok(successResponse(
                "AI generation analysis completed", analysis));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable String assetId) {
        assetManagementService.deleteOwnedAsset(assetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{assetId}/download")
    public ResponseEntity<InputStreamResource> downloadAsset(
            @PathVariable String assetId) {
        AssetQueryService.AssetDownload download = assetQueryService.openOwnedAssetDownload(assetId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(safeMediaType(download.mimeType()))
                .body(new InputStreamResource(download.inputStream()));
    }

    private ResponseEntity<ApiResponse<AssetResponse>> created(String message, AssetResponse asset) {
        ApiResponse<AssetResponse> response = ApiResponse.<AssetResponse>builder()
                .success(true)
                .message(message)
                .data(asset)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private <T> ApiResponse<T> successResponse(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private MediaType safeMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
