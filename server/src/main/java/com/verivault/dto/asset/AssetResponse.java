package com.verivault.dto.asset;

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

    private String uuid;
    private String title;
    private String description;
    private String assetType;
    private String fileName;
    private Long fileSize;
    private String sha256Hash;
    private LocalDateTime uploadDate;
    private String verificationStatus;
}
