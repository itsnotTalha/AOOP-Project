package com.authvault.dto.asset;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetResponse {

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
}
