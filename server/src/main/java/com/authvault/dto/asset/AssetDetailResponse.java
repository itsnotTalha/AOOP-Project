package com.authvault.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDetailResponse {

    private String assetId;
    private String title;
    private String description;
    private String assetType;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private String sha256Hash;
    private String verificationStatus;
    private LocalDateTime uploadDate;
    private LocalDateTime lastVerifiedAt;
    private List<VerificationHistoryResponse> verificationHistory;
}
