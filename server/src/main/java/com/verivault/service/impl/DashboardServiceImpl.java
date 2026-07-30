package com.verivault.service.impl;

import com.verivault.dto.dashboard.DashboardSummaryResponse;
import com.verivault.entity.DigitalAsset;
import com.verivault.entity.Notification;
import com.verivault.entity.User;
import com.verivault.entity.VeriWallet;
import com.verivault.exception.ResourceNotFoundException;
import com.verivault.repository.DigitalAssetRepository;
import com.verivault.repository.NotificationRepository;
import com.verivault.repository.VeriWalletRepository;
import com.verivault.security.util.SecurityUtils;
import com.verivault.service.DashboardService;
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