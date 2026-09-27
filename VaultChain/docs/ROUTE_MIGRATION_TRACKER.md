# Route migration tracker

Source: `SE-Lab-Project/feature/document-module` at commit `0839b084`. Status reflects the local Spring working tree on 2026-09-26.

| Original Route | Spring Controller | Service | Repository | Status |
| --- | --- | --- | --- | --- |
| POST /api/assets/upload | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| POST /api/assets/check | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets/:id/metadata | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets/:id/hash | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets/:id/ownership-history | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets/:id/content | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/assets/:id | AssetController | AssetServiceImpl | AssetJpaRepository, AssetMetadataJpaRepository, AssetHashJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| POST /api/auth/register | AuthController | AuthService | AuthRepository | Implemented |
| POST /api/auth/login | AuthController | AuthService | AuthRepository | Implemented |
| GET /api/auth/me | AuthController | AuthService | AuthRepository | Implemented |
| PATCH /api/auth/profile | AuthController | AuthService | AuthRepository | Implemented |
| PATCH /api/auth/password | AuthController | AuthService | AuthRepository | Implemented |
| POST /api/auth/logout | AuthController | AuthService | AuthRepository | Implemented |
| GET /api/dashboard/summary | DashboardController | DashboardServiceImpl | DashboardReadRepository | Implemented |
| POST /api/documents | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents/:id/content | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents/:id/preview | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents/:id/ocr | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| POST /api/documents/:id/ocr | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| POST /api/documents/:id/verify | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents/:id/report | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/documents/:id | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| DELETE /api/documents/:id | DocumentController | DocumentServiceImpl | DocumentJpaRepository, OcrResultJpaRepository, DocumentVerificationJpaRepository | Implemented; parity pending |
| GET /api/health | HealthController | HealthService | HealthRepository | Implemented |
| POST /api/marketplace/listings | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/marketplace/listings | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/marketplace/listings/:reference/content | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| POST /api/marketplace/listings/:reference/purchase | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| GET /api/marketplace/listings/:reference | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| PATCH /api/marketplace/listings/:reference | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| DELETE /api/marketplace/listings/:reference | MarketplaceController | MarketplaceServiceImpl | MarketplaceListingJpaRepository, AssetJpaRepository, WalletJpaRepository, OwnershipHistoryJpaRepository | Implemented; parity pending |
| POST /api/vaults | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| GET /api/vaults | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| GET /api/vaults/:reference | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/vaults/:reference/unlock | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/vaults/:reference/lock | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/vaults/:reference/change-password | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/vaults/:reference/reset-password | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| PATCH /api/vaults/:reference | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| DELETE /api/vaults/:reference | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/vaults/:reference/assets | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| DELETE /api/vaults/:reference/assets/:assetId | VaultController | VaultServiceImpl | VaultJpaRepository, VaultAssetJpaRepository, VaultUnlockSessionJpaRepository, VaultUnlockAttemptJpaRepository | Implemented; parity pending |
| POST /api/verifications | VerificationController | VerificationServiceImpl | VerificationReportJpaRepository, AssetHashJpaRepository | Implemented; parity pending |
| GET /api/verifications | VerificationController | VerificationServiceImpl | VerificationReportJpaRepository, AssetHashJpaRepository | Implemented; parity pending |
| GET /api/verifications/:reference | VerificationController | VerificationServiceImpl | VerificationReportJpaRepository, AssetHashJpaRepository | Implemented; parity pending |
| GET /api/wallet | WalletController | WalletServiceImpl | WalletJpaRepository, WalletTransactionJpaRepository | Implemented |
| GET /api/wallet/transactions | WalletController | WalletServiceImpl | WalletJpaRepository, WalletTransactionJpaRepository | Implemented |
| POST /api/wallet/transactions | WalletController | WalletServiceImpl | WalletJpaRepository, WalletTransactionJpaRepository | Implemented |

Mapped: 50/50; missing route mappings: 0/50. Functional parity remains under audit, especially OCR, image format metadata, and marketplace concurrency.
