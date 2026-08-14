# Asset Management and Verification History MVP Contract

This document is the source of truth for the asset-management and verification-history phase. It extends, but does not replace, `docs/UPLOAD_INTEGRITY_MVP.md`. The existing upload and SHA-256 integrity workflows must remain intact.

The public `assetId` is always the `DigitalAsset.uuid`. Database IDs, stored filenames, storage keys, and absolute filesystem paths must never appear in API requests or responses.

For this phase, an asset is owned by the authenticated user when that user is its current owner. All ownership checks must use the current user obtained from the existing JWT security context; clients must never supply a user ID.

## 1. My Assets

### Endpoint

`GET /api/v1/assets`

### Optional Query Parameters

| Parameter | Accepted values | Default | Behavior |
| --- | --- | --- | --- |
| `type` | `IMAGE`, `DOCUMENT` | All types | Exact `AssetType` match |
| `status` | `PENDING`, `VERIFIED`, `REJECTED` | All statuses | Exact `VerificationStatus` match |
| `search` | Free text | No search filter | Case-insensitive substring match against title or original display filename |
| `sort` | `newest`, `oldest` | `newest` | Sort by upload date descending or ascending |

Filters may be combined and must use AND semantics. Leading and trailing search whitespace must be ignored; a blank search is equivalent to no search filter. Unsupported enum or sort values must return HTTP `400 Bad Request` through the existing error envelope.

`type` and `status` use the existing strict enum convention and therefore require the uppercase values shown above. `sort` requires the lowercase values shown above.

The endpoint must:

- Return only assets whose current owner is the authenticated user.
- Use newest-first ordering when `sort` is absent.
- Return DTOs only.
- Expose `assetId` as the public asset UUID and never expose a database ID.
- Never expose `storedFilename` or `storagePath`.
- Return the existing list metadata required by the UI: `assetId`, `title`, `description`, `assetType`, `originalFilename`, `mimeType`, `fileSize`, `sha256Hash`, `verificationStatus`, and `uploadDate`.
- Preserve the existing common success envelope.

Pagination is not part of this phase.

## 2. Asset Details

### Endpoint

`GET /api/v1/assets/{assetId}`

The endpoint must retrieve the asset by public UUID and authenticated current owner in the same owner-scoped operation. A nonexistent UUID or an asset not owned by the current user must return HTTP `404 Not Found` without revealing whether another user's asset exists.

The detail DTO must return:

- `assetId`
- `title`
- `description`
- `assetType`
- `originalFilename`
- `mimeType`
- `fileSize`
- `sha256Hash`
- `verificationStatus`
- `uploadDate`
- `lastVerifiedAt`, nullable when no verification history exists

`lastVerifiedAt` is the greatest `verifiedAt` timestamp from the asset's verification history. The existing detail response may retain its embedded history during a compatibility transition, but the dedicated verification-history endpoint below is the canonical source for history.

For this MVP, `lastVerifiedAt` is detail-only. The list DTO remains unchanged so list retrieval does not introduce per-asset history queries or a more complex aggregate projection.

The response must use DTOs and the existing common success envelope. It must not expose JPA entities, internal IDs, storage fields, or verifier database IDs.

## 3. Verification History

### Endpoint

`GET /api/v1/assets/{assetId}/verification-history`

The endpoint must:

- Require authentication.
- Resolve the asset by public UUID and authenticated current owner before reading history.
- Return HTTP `404 Not Found` for both nonexistent and non-owned assets.
- Return history newest first by `verifiedAt`.
- Return DTOs through the existing common success envelope.
- Never expose history, asset, user, or other database IDs.

Each history item must return:

- `verificationMethod`
- `result`
- `verifiedAt`
- `notes`, containing only concise, safe text

The current model records `verifiedBy`, but the existing public verification-history DTO intentionally omits verifier identity. This phase should continue omitting it because every supported verification is owner-initiated and no established public verifier-identity DTO exists. If verifier identity is added later, it may use only an already-approved public user UUID/display field and must never expose a database ID, email address, username, or security principal details.

Safe notes must not contain exceptions, absolute paths, storage keys, internal IDs, or other implementation details.

### Existing Integrity Verification

`POST /api/v1/assets/{assetId}/verify-integrity`

The existing SHA-256 integrity endpoint and its response contract remain unchanged unless a narrowly scoped compatibility change is required. It must continue to enforce current-owner access, update the asset status, and persist a `SHA256_INTEGRITY` history record.

## 4. Download and Secure Preview Source

### Endpoint

`GET /api/v1/assets/{assetId}/download`

The endpoint must continue to:

- Require authentication and current-owner access.
- Resolve assets by public UUID, never database ID.
- Stream the stored file instead of loading all bytes into memory.
- Use a sanitized original display filename in `Content-Disposition`.
- Return `Cache-Control: no-store`.
- Return only a safe media type and file bytes; never expose the storage path.
- Return HTTP `404 Not Found` for nonexistent or non-owned assets.

The frontend may reuse this authenticated binary endpoint as its preview source by requesting a Blob through the existing Axios/JWT interceptor and creating a temporary browser object URL. Object URLs must be revoked when replaced or when the detail view unmounts.

## 5. Delete

### Endpoint

`DELETE /api/v1/assets/{assetId}`

Deletion is a hard delete. Soft delete must not be introduced.

The delete workflow must:

1. Resolve the asset by public UUID and authenticated current owner.
2. Return HTTP `404 Not Found` for a nonexistent or non-owned asset.
3. Remove the physical stored file using controlled storage operations that enforce the configured upload root.
4. Delete the `DigitalAsset` database record.
5. Delete its dependent `Document` record when present.
6. Delete its dependent `VerificationHistory` records using the existing JPA cascade and database foreign-key design.
7. Return HTTP `204 No Content` with no response body after success.

The workflow must not use a client filename or client path to locate the stored file. It must not return a storage path in success or error responses.

Because filesystem and database operations cannot share one atomic transaction, deletion must use reasonable compensation:

- Prefer staging or moving the controlled stored file to a generated, controlled temporary deletion location before database deletion.
- Permanently remove the staged file only after the database transaction commits.
- Restore the staged file to its original controlled location if the database transaction rolls back.
- If the physical file is already missing, allow deletion of the stale database record and its dependents.
- If a storage safety check or staging operation fails, do not delete the database record; return a safe server error without leaking a path.

The existing upload cleanup method is not, by itself, a complete public deletion workflow because it is best-effort, silent, and designed only to compensate for failed upload persistence.

## 6. Frontend Target

### Routes

- `/assets` remains the upload and asset-list route.
- `/assets/{assetId}` is the owner-only asset-detail route.

Both routes remain protected by the existing `ProtectedRoute` and render inside `DashboardLayout`. The existing Assets navigation entry remains `/assets`.

### Asset List

The page must retain upload functionality and show the authenticated user's assets with:

- Image or document indicator
- Title
- Original display filename
- File size
- Verification status
- Upload date

It must add:

- Search by title or filename
- Type filter: all, image, or document
- Status filter: all, pending, verified, or rejected
- Newest/oldest sorting

The UI must send the selected criteria to `GET /api/v1/assets`; it must not bypass owner filtering or issue direct Axios calls from React components. Loading, empty, filtered-empty, and error states must be distinct and understandable.

### Asset Detail View

The protected `/assets/{assetId}` route provides the selected-asset detail view. Asset list rows navigate to it using the public asset UUID; no public asset route exists.

The detail view must provide:

- Supported preview
- Asset metadata
- SHA-256 displayed in monospace with a copy action
- `lastVerifiedAt` when available
- Verification history, newest first
- Verify Integrity action
- Download action
- Delete action with explicit confirmation

After verification, the detail, list status, and history must refresh. After deletion, the detail view must close and the removed asset must disappear from the list.

### Preview Rules

- JPEG and PNG assets may render in an `<img>` from an authenticated Blob/object URL.
- PDF assets may render in a browser `<iframe>`, `<embed>`, or `<object>` from an authenticated Blob/object URL when the browser supports it.
- If secure inline rendering is unavailable, show a clear preview-unavailable state and retain Download.
- Never place a server filesystem path, storage key, or manually constructed unauthenticated asset URL in the DOM.
- Revoke Blob object URLs when no longer needed.

### Frontend Service Boundary

Extend the existing `assetService` with query-aware listing, dedicated history retrieval, deletion, and browser download/preview helpers as needed. All calls must use the shared Axios instance and its JWT interceptor. React pages and components must not import Axios or the shared API client directly.

## 7. API and Error Conventions

- JSON success responses continue using the existing `ApiResponse<T>` envelope.
- JSON errors continue using the existing `ErrorResponse` envelope.
- Binary download responses and `204 No Content` deletion responses do not use a JSON success envelope.
- Missing or invalid authentication returns HTTP `401 Unauthorized` through the existing JWT entry point.
- Invalid list criteria return HTTP `400 Bad Request`.
- Nonexistent and non-owned assets return HTTP `404 Not Found` to avoid asset enumeration.
- Storage failures return a safe server error without absolute paths, storage keys, or exception details.
- JPA entities must never be serialized directly.

## 8. Non-Goals

This phase does not include:

- Editing or replacing uploaded files
- Sharing
- Public assets
- Marketplace functionality
- Ownership transfer
- Fractional ownership
- Blockchain functionality
- Secure vault functionality
- AI verification or authenticity scoring
- pHash or visual similarity
- OCR
- Semantic hashing
- Metadata-authenticity claims
- Bulk actions
- Pagination

## 9. Engineering Rules

- Preserve the working upload and SHA-256 integrity implementation.
- Reuse existing entities, repositories, DTOs, services, storage protections, security utilities, API envelopes, and exception handling before adding abstractions.
- Keep controllers thin; filtering, ownership, history, and deletion rules belong in services.
- Always obtain the current user through the existing JWT/security mechanism.
- Keep storage access behind `AssetStorageService`.
- React components call `assetService`; they do not call Axios directly.
- Make minimal, focused changes and do not rewrite working upload code.
- Add focused authorization, filtering, history-ordering, deletion-compensation, and frontend-build coverage.
- Run relevant backend tests and the frontend production build after each implementation task.

## Current Implementation Audit

| Capability | Existing | Partial | Missing | Relevant Files |
| --- | :---: | :---: | :---: | --- |
| `DigitalAsset` model with UUID, owner/current owner, metadata, SHA-256, status, document, and history relations | Yes | — | — | `server/src/main/java/com/authvault/entity/DigitalAsset.java`, `server/database/schema.sql` |
| `Document` one-to-one relation for document assets | Yes | — | — | `server/src/main/java/com/authvault/entity/Document.java`, `server/database/schema.sql` |
| `VerificationHistory` model with SHA-256 methods, result, notes, verifier, and timestamp | Yes | — | — | `server/src/main/java/com/authvault/entity/VerificationHistory.java`, `server/database/schema.sql` |
| Owner-scoped UUID lookup and newest-first owner listing repositories | Yes | — | — | `server/src/main/java/com/authvault/repository/DigitalAssetRepository.java` |
| Filtered/searchable/oldest-first owner listing repository behavior | Yes | — | — | `DigitalAssetRepository.java` supports JPA Specifications; `AssetQueryServiceImpl.java` builds one owner-scoped criteria query and repository sort |
| Verification-history repository access | Yes | — | — | `VerificationHistoryRepository.java` provides newest-first asset history retrieval |
| Asset list/detail/history DTO safety | Yes | — | — | `AssetResponse.java`, `AssetDetailResponse.java`, `VerificationHistoryResponse.java`; DTOs hide IDs/paths and detail exposes `lastVerifiedAt` while list intentionally does not |
| Shared validated image/document upload and upload history creation | Yes | — | — | `AssetUploadService.java`, `AssetUploadServiceImpl.java`, `UploadFileValidator.java`, `Sha256Service.java` |
| Owner-scoped SHA-256 integrity verification and history persistence | Yes | — | — | `AssetIntegrityService.java`, `AssetIntegrityServiceImpl.java` |
| Owner-only list endpoint with filters/search/sorting | Yes | — | — | `AssetUploadController.java`, `AssetQueryService.java`, `AssetQueryServiceImpl.java` |
| Owner-only asset detail endpoint | Yes | — | — | `AssetUploadController.java`, `AssetQueryServiceImpl.java`; metadata, `lastVerifiedAt`, and compatibility history are present |
| Dedicated owner-only verification-history endpoint | Yes | — | — | `AssetUploadController.java`, `AssetQueryService.java`, `AssetQueryServiceImpl.java` |
| Owner-only streamed download with safe filename and `no-store` | Yes | — | — | `AssetUploadController.java`, `AssetQueryServiceImpl.java`, `AssetStorageService.java`, `LocalAssetStorageService.java` |
| Authenticated current-user resolution | Yes | — | — | `SecurityUtils.java`, `JwtAuthenticationFilter.java`, `CustomUserDetails.java`, `SecurityConfig.java` |
| Common success/error envelopes and safe HTTP mappings | Yes | — | — | `ApiResponse.java`, `ErrorResponse.java`, `GlobalExceptionHandler.java`, `JwtAuthenticationEntryPoint.java` |
| Owner-only hard-delete endpoint and business workflow | Yes | — | — | `AssetUploadController.java`, `AssetManagementService.java`, `AssetManagementServiceImpl.java` |
| Controlled physical-file deletion with database rollback compensation | Yes | — | — | `AssetStorageService.java`, `LocalAssetStorageService.java` stage generated quarantine files, restore on rollback/failure, and clean after commit |
| JPA/database cleanup of dependent Document and VerificationHistory | Yes | — | — | `DigitalAsset.java` uses cascade-all relations; `schema.sql` uses `ON DELETE CASCADE` for both dependents |
| Protected `/assets` and `/assets/:assetId` routes and dashboard navigation | Yes | — | — | `AppRoutes.jsx`, `ProtectedRoute.jsx`, `DashboardLayout.jsx` |
| Assets upload/list/status/integrity UI with responsive states | Yes | — | — | `Assets.jsx`, `AssetUploadForm.jsx`, `AssetList.jsx` |
| Asset frontend service using shared Axios/JWT interceptor | Yes | — | — | `assetService.js`, `api.js`; query-aware list, detail, history, verify, authenticated Blob download, and delete calls use the shared client |
| Search/type/status/sort controls | Yes | — | — | `Assets.jsx`, `AssetFilters.jsx`, `AssetList.jsx`, `assetService.js`; URL-backed controls call the backend criteria endpoint, debounce search, and preserve active filters after uploads |
| Asset detail metadata/history/verify-again view | Yes | — | — | `AssetDetails.jsx`, `VerificationHistoryList.jsx`; status and `lastVerifiedAt` update after verification and history is refreshed |
| Download UI | Yes | — | — | `AssetDetails.jsx`, `assetService.js`; authenticated Blob download uses the safe response filename and revokes its temporary URL |
| Image/PDF preview UI | Yes | — | — | `AssetPreview.jsx`; private files are fetched as authenticated Blobs, rendered through object URLs, and revoked on change/unmount |
| Confirmed delete UI and post-success navigation | Yes | — | — | `DeleteAssetDialog.jsx`, `AssetDetails.jsx`, `assetService.js`; deletion requires confirmation and returns to `/assets` only after success |
| Reusable UI building blocks | — | Yes | — | Dashboard/Tailwind/Lucide patterns and auth-oriented `FormField`/`SubmitButton` exist; asset-specific status/hash helpers are currently local functions rather than shared components |
| Authorization and upload-integrity integration coverage | Yes | — | — | `server/src/test/java/com/authvault/controller/AssetUploadControllerIntegrationTest.java` and focused service/storage/validation tests |
| Filtering and dedicated-history backend coverage | Yes | — | — | `server/src/test/java/com/authvault/controller/AssetUploadControllerIntegrationTest.java` |
| Backend deletion coverage | Yes | — | — | `AssetUploadControllerIntegrationTest.java`, `AssetManagementServiceImplTest.java`, `LocalAssetStorageServiceTest.java` |
| Frontend asset-management build coverage | Yes | — | — | The production Vite build covers the completed routes and components; the project intentionally has no frontend test or lint framework |

### Proposed Implementation Order

No Asset Management MVP implementation steps remain. The next task is a focused phase audit: run the complete backend test suite and frontend production build, then verify ownership, DTO/path safety, URL query behavior, Blob URL cleanup, deletion compensation, and conformance with this document and `UPLOAD_INTEGRITY_MVP.md`.

### Completion Status

The Asset Management MVP contract documented here is complete. Upload/list behavior, backend filtering, owner-only details/history/download/delete, secure previews, re-verification, and URL-backed frontend search/filter/sort controls are implemented. Pagination and the other listed non-goals remain outside this MVP.
