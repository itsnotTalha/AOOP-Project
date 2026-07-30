package com.verivault.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private long totalAssets;
    private long verifiedAssets;
    private BigDecimal walletBalance;
    private long storageUsed;
    private long notifications;
}