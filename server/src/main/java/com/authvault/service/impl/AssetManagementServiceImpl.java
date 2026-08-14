package com.authvault.service.impl;

import com.authvault.entity.DigitalAsset;
import com.authvault.entity.User;
import com.authvault.exception.ResourceNotFoundException;
import com.authvault.repository.DigitalAssetRepository;
import com.authvault.security.util.SecurityUtils;
import com.authvault.service.AssetManagementService;
import com.authvault.service.AssetStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Service
public class AssetManagementServiceImpl implements AssetManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetManagementServiceImpl.class);

    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetStorageService assetStorageService;

    public AssetManagementServiceImpl(
            DigitalAssetRepository digitalAssetRepository,
            AssetStorageService assetStorageService) {
        this.digitalAssetRepository = digitalAssetRepository;
        this.assetStorageService = assetStorageService;
    }

    @Override
    @Transactional
    public void deleteOwnedAsset(String assetId) {
        User currentUser = SecurityUtils.getCurrentUser();
        DigitalAsset asset = digitalAssetRepository.findByUuidAndCurrentOwner(assetId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Optional<AssetStorageService.StagedDeletion> stagedDeletion =
                assetStorageService.stageStoredAssetForDeletion(asset.getStoragePath());
        if (stagedDeletion.isEmpty()) {
            LOGGER.warn("Stored file was already missing while deleting an owned asset");
        }

        boolean transactionSynchronizationRegistered = false;
        try {
            if (stagedDeletion.isPresent()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                registerDeletionSynchronization(stagedDeletion.get());
                transactionSynchronizationRegistered = true;
            }

            digitalAssetRepository.delete(asset);
            digitalAssetRepository.flush();

            if (stagedDeletion.isPresent() && !transactionSynchronizationRegistered) {
                assetStorageService.deleteStagedDeletionQuietly(stagedDeletion.get());
            }
        } catch (RuntimeException exception) {
            stagedDeletion.ifPresent(this::restoreQuietly);
            throw exception;
        }
    }

    private void registerDeletionSynchronization(
            AssetStorageService.StagedDeletion stagedDeletion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    assetStorageService.deleteStagedDeletionQuietly(stagedDeletion);
                } else {
                    restoreQuietly(stagedDeletion);
                }
            }
        });
    }

    private void restoreQuietly(AssetStorageService.StagedDeletion stagedDeletion) {
        try {
            assetStorageService.restoreStagedDeletion(stagedDeletion);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not restore a staged asset file after database deletion failure");
        }
    }
}
