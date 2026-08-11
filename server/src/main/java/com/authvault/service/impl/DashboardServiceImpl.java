package com.authvault.service.impl;

import com.authvault.dto.dashboard.DashboardSummaryResponse;
import com.authvault.entity.DigitalAsset;
import com.authvault.entity.Notification;
import com.authvault.entity.User;
import com.authvault.entity.VeriWallet;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.repository.NotificationRepository;
import com.authvault.repository.VeriWalletRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.DashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final VeriWalletRepository veriWalletRepository;
    private final NotificationRepository notificationRepository;

    public DashboardServiceImpl(DigitalAssetRepository digitalAssetRepository,
                                VeriWalletRepository veriWalletRepository,
                                NotificationRepository notificationRepository) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.veriWalletRepository = veriWalletRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public DashboardSummaryResponse getSummary() {
        User currentUser = SecurityUtils.getCurrentUser();
        Long userId = currentUser.getId();

        if (userId == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        List<DigitalAsset> assets = digitalAssetRepository.findByCurrentOwnerId(userId);
        long totalAssets = assets.size();
        long verifiedAssets = assets.stream()
                .filter(asset -> asset.getVerificationStatus() == DigitalAsset.VerificationStatus.VERIFIED)
                .count();
        long storageUsed = assets.stream()
                .map(DigitalAsset::getFileSize)
                .filter(fileSize -> fileSize != null)
                .mapToLong(Long::longValue)
                .sum();

        BigDecimal walletBalance = veriWalletRepository.findByUserId(userId)
                .map(VeriWallet::getBalance)
                .orElse(BigDecimal.ZERO);

        long notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size();

        return DashboardSummaryResponse.builder()
                .totalAssets(totalAssets)
                .verifiedAssets(verifiedAssets)
                .walletBalance(walletBalance)
                .storageUsed(storageUsed)
                .notifications(notifications)
                .build();
    }
}