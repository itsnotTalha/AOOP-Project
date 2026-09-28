# VaultChain Spring Boot migration contract

Audit dates: 2026-09-20–2026-09-21. Scope: **Prompt 0 only**. This is a contract for a future implementation, not a claim of Java parity. Paths below are relative to `VaultChain/`. The executable reference is `server/`; the proposed implementation belongs in `spring-server/`. Do not switch runtime scripts until cross-backend tests pass.

## 1. Evidence and scope

Directly inspected all 56 JavaScript/SQL files in `server/src/` (routes, controllers, middleware, services, repositories, configuration, database initialization and schema), all eight `server/tests/*.test.js` files, and all nine `client/src/services/*.js` files. Also inspected package manifests/lockfile, frontend API constants/Vite configuration, and the admin promotion script. Coverage validation found all **64 explicit endpoints**, **22 tables**, **28 base indexes** (plus migration-added indexes), and **53 named reference tests** represented in the documents. Relative document links and fenced blocks were also checked. The companion [parity checklist](SPRING_BOOT_PARITY_CHECKLIST.md) maps every named Node test to a proposed Java test.

Verified repository layout:

- React/Vite is in `VaultChain/client/`, not the workspace-root `client/` shown in older Git history. Vite uses the React plugin and has no API proxy configured.
- Express is in `VaultChain/server/`. `server/src/server.js` initializes SQLite before listening; `server/src/app.js` mounts everything at `/api`.
- Database schema is `server/src/database/schema.sql`. Migrations are **functions in `server/src/database/init.js`**, re-exported by `initDatabase.js`; `database/migrations/` and `database/seed/` contain only placeholders. There is no migration version table.
- Both `server/src/database/sqlite.db` and `vaultchain.sqlite` exist. Code defaults to **vaultchain.sqlite**; file existence does not prove which is deployed. No database was opened for writes or initialized during this audit.
- `models/`, `validators/`, `utils/`, and the analytics/blockchain/encryption/notification service directories contain placeholders. Their schema tables are not evidence of implemented APIs.
- The workspace has extensive pre-existing Git deletions and an untracked `VaultChain/` tree. The contract describes the current files on disk, not the old root-level Java project in Git history.

The reference dependencies are not installed in this checkout. The lockfile pins `image-hash` 5.3.2, `exifr` 7.1.3, `jpeg-js` 0.4.4, the image-hash `pngjs` dependency 6.0.0, Tesseract.js 7.0.0, English language data 1.0.0, Express 4.22.2, and jsonwebtoken 9.0.3. Package manifest ranges alone are insufficient to reproduce image fingerprints. Full integration execution and dependency-internal image algorithm verification remain release gates, explicitly listed below.

## 2. Common HTTP contract

Default frontend base URL: `http://localhost:3000/api`; override: `VITE_API_URL`. Every endpoint in the tables uses its **full** path. Trailing slash forms are accepted by the existing Express routers. No pagination is implemented on ordinary lists; unknown query fields are generally ignored.

Notation: `S{...}` means `{ "success": true, ... }`; `E(message)` means exactly `{ "success": false, "message": message }`. Symbols such as `Asset`, `Vault`, and `Document` are field-complete shapes defined below. `?` marks an optional input or an explicitly conditional output, not a nullable field. Null and omitted properties are different. JSON responses use JSON numbers/booleans rather than strings for mapped numeric/boolean values; raw SQL rows retain SQLite's 0/1 flags and nullable aggregates. Dates generally remain SQLite `YYYY-MM-DD HH:mm:ss` strings; unlock expirations are ISO UTC with milliseconds. Do not globally convert all timestamps to Java's default date serialization.

All routes except health/register/login require `Authorization: Bearer <token>` and a current, non-suspended database account; no additional role restriction applies outside `/api/admin`. Ownership failures normally return 404, even for admins. `review` accounts remain usable.

Common errors apply to **each protected endpoint**, in addition to route-specific errors:

| Status | Exact message / condition |
| --- | --- |
| 401 | `Unauthorized` — missing token or scheme other than exactly `Bearer` |
| 401 | `Invalid or expired token` — verification failure |
| 401 | `Account no longer exists` — JWT id has no database row |
| 403 | `This account has been suspended` |
| 403 | `You do not have permission to perform this action` — role gate |
| 404 | `Route not found` — unmatched route, including obsolete routes |
| 500 | `Internal Server Error` only when the thrown error has no message |

`errorHandler` uses `error.status || error.statusCode || 500` and exposes `error.message`. Internal service `code`, `duplicateAssetId`, causes, and stacks are **not** HTTP error fields. SQL and filesystem errors may expose dependency-specific messages; record their actual Node outputs before attempting byte-for-byte Java error matching. Express JSON parsing is enabled with defaults (including its default body-size ceiling); malformed/oversized JSON must use the same error envelope. No form-urlencoded parser, static upload mount, cookie login, CSRF flow, refresh-token API, or global JWT revocation list exists. `cors()` and `helmet()` apply globally; preserve browser cross-origin access and content security headers. Dependency-generated HEAD/OPTIONS, ETag, range and conditional responses need wire-level capture, not invented controller responses.

## 3. Endpoint inventory

### 3.1 Health and authentication

Source: [auth routes](../server/src/routes/authRoutes.js), [auth service](../server/src/services/auth/authService.js), [auth controller](../server/src/controllers/auth/authController.js).

| Method | Exact path | Auth | Input | Success | Route errors |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/health` | Public | None | 200 `S{message:"VaultChain API running"}` | None explicitly |
| POST | `/api/auth/register` | Public | JSON `fullName` or `full_name`, `email`, `password` | 201 `S{message:"User registered successfully",token,user:User}` | 400 registration validation; 409 duplicate email |
| POST | `/api/auth/login` | Public | JSON `email,password` | 200 `S{message:"Login successful",token,user:User}` | 400 missing fields; 401 invalid credentials; 403 suspended |
| GET | `/api/auth/me` | Bearer | None | 200 `S{user:MeUser}` | 404 `User not found` if removed after authentication |
| PATCH | `/api/auth/profile` | Bearer | JSON `fullName` or `full_name`, `email` (both required) | 200 `S{message:"Profile updated successfully",user:User}` | 400 profile validation; 409 duplicate; 404 user |
| PATCH | `/api/auth/password` | Bearer | JSON `currentPassword,newPassword` | 200 `S{message:"Password changed successfully"}` | 400 password validation; 401 current password wrong; 404 user |
| POST | `/api/auth/logout` | Bearer | None | 200 `S{message:"Logged out successfully"}` | Common errors |

`User = {id,fullName,email,role,status,createdAt,updatedAt}`. `MeUser = {id,full_name,email,role,status,created_at}`. Password hashes never appear. Registration ignores supplied role/status and creates a USER/active account and zero-balance wallet in one serialized database transaction. Email is `String(value || '').trim().toLowerCase()`; name is trimmed. Registration checks in order: `Full name is required`, `Email is required`, `Email is invalid` (regex `^\S+@\S+\.\S+$`), `Password is required`, `Password must be at least 8 characters long` (all 400), then `Email is already registered` (409). Registration does not impose the profile length caps.

Profile checks: `Full name is required`, `Full name must be 100 characters or fewer`, `Email is required`, `Email is invalid` (email >254 or bad regex), all 400. Duplicate email is 409. Login requires email/password but does not run the registration email regex; unknown email or incorrect password both yield 401 `Invalid email or password`. Password change errors: 400 `Current password is required`, `New password is required`, `New password must be at least 8 characters long`, `New password must be different from the current password`; 401 `Current password is incorrect`.

### 3.2 Assets

Source: [asset controller](../server/src/controllers/asset/assetController.js), [service](../server/src/services/asset/assetService.js), [repository](../server/src/repositories/assetRepository.js). All require Bearer; no role gate.

| Method | Exact path | Input | Success | Route errors |
| --- | --- | --- | --- | --- |
| POST | `/api/assets/upload` | Multipart `file,title,category`, optional `description` | 201 `S{message:"Asset uploaded successfully",asset:Asset,hash:Hashes,metadata:MetadataRecord,pixelCount}` | 400 title/category/file; 409 duplicate; upload/hash errors |
| POST | `/api/assets/check` | Multipart `file` | 200 `S{result:OwnershipCheck}` | 400 `Image file is required`; upload/hash errors |
| GET | `/api/assets` | None | 200 `S{assets:Asset[]}` | Common errors |
| GET | `/api/assets/:id` | Numeric id | 200 `S{asset:Asset}` | 400 invalid id; 404 asset |
| GET | `/api/assets/:id/metadata` | Numeric id | 200 `S{metadata:MetadataResponse}` | 400 invalid id; 404 asset/metadata; 423 protection |
| GET | `/api/assets/:id/hash` | Numeric id | 200 `S{hashes:Hashes}` | 400 invalid id; 404 asset/hash; 423 protection |
| GET | `/api/assets/:id/ownership-history` | id converted to Number | 200 `S{history:OwnershipHistory[]}` | 404 `Asset not found` (also invalid ids; differs from other asset endpoints) |
| GET | `/api/assets/:id/content` | Numeric id | 200 file bytes, with sendFile conditional/range behavior | 400 invalid id; 404 asset/file; 423 protection |

`Asset = {id,title,description,category,fileName,fileSize,mimeType,status,createdAt,updatedAt,width,height,sha256,phash,hasHash,hasMetadata,contentUrl,vaultProtection}`. No owner id or filesystem path. `contentUrl` is `/api/assets/{id}/content`. List sorting is `created_at DESC,id DESC`. Joins allow assets without metadata/hash rows: flags false and joined fields null. No filtering by asset status occurs.

`Hashes = {sha256,phash,alreadyUploadedBefore:false,duplicateAssetId:null}`. `MetadataRecord = {assetId,width,height,camera,location,createdDate,pixelCount,patterns,metadataJson}`. The standalone metadata endpoint instead returns `MetadataResponse = {width,height,pixelCount,pixel_count,patterns,camera,location,created_date,metadata_json}`. Preserve both pixel-count aliases. Upload's top-level pixelCount falls back to metadataRecord.pixelCount (Asset has no pixelCount property).

`vaultProtection` from ordinary reads is `{passwordProtected,isLocked,unlockExpiresAt,protectingVaults:[{reference,name,isLocked,unlockExpiresAt}]}`. Upload uses `{passwordProtected:false,isLocked:false,protectingVaults:[]}` without `unlockExpiresAt`. A locked ordinary asset retains its identifying/display fields and flags, but width/height/sha256/phash/contentUrl are null; detail itself still returns 200. Protected content/hash/metadata return 423.

Validation errors: 400 `Title is required`, `Category is required`, `File is required`, `Asset id must be a positive integer`; 404 `Asset not found`, `Asset metadata not found`, `Asset hash not found`, internal `Asset hash record not found`. Title/category are trimmed; description becomes trimmed text if truthy, otherwise null (repository also converts empty string to null). No category enumeration or title length cap. Duplicate means **exact equal stored pHash globally**, selecting earliest `created_at,asset_id`; not SHA equality or threshold-based similarity. 409 owner message: `This image was already uploaded before as asset id {id}`; other owner: `This image was already uploaded to VaultChain`. Upload is currently multiple non-transactional inserts/upserts, with no general failure cleanup. Do not confuse this with temporary-check cleanup.

OwnershipCheck miss: `{match:false,matchType:null,checked:{sha256,phash},asset:null}`. Match: `{match:true,matchType:"exact"|"perceptual",similarity,distance,threshold,hashBits,checked:{sha256,phash},asset:{id,reference,title,registeredAt,owner:{isCurrentUser,label}}}`. Exact SHA lookup runs first, earliest `created_at,id`; pHash is not generated, `checked.phash:null`, comparison fields null. Otherwise nearest bit-distance candidate wins; tie preserves candidate order (asset id ascending); invalid stored pHashes are skipped. At distance 0 similarity is `identical`; at 1..strong it is `strong`; at strong+1..possible it is `possible`; outside possible is a miss. Threshold is strong for identical/strong and possible for possible. Cross-owner id/title are null; owner label is HMAC pseudonym; own id/title remain visible even if Vault-locked. Never return the registered hashes, private owner identity, or paths. Both success and failure inside processing delete the check file in `finally` (unlink errors are swallowed). Checks never create assets or reports.

### 3.3 Vaults

Source: [vault service](../server/src/services/vault/vaultService.js), [access service](../server/src/services/vault/vaultAccessService.js), [repository](../server/src/repositories/vaultRepository.js). Bearer and Vault ownership apply throughout.

| Method | Exact path | JSON input / extra access | Success | Route errors |
| --- | --- | --- | --- | --- |
| POST | `/api/vaults` | `name,password`, optional `description,autoLockMinutes` | 201 `S{vault:Vault}` (locked) | 400 validators; 500 reference allocation |
| GET | `/api/vaults` | None | 200 `S{vaults:Vault[],stats:VaultStats}` | Common errors |
| GET | `/api/vaults/:reference` | None | 200 `S{vault:Vault}` | 404 Vault |
| POST | `/api/vaults/:reference/unlock` | `password` | 200 `S{vault:Vault}` | 404; 409 legacy; 401 wrong; 429 attempts |
| POST | `/api/vaults/:reference/lock` | None | 200 `S{vault:Vault}` | 404 |
| POST | `/api/vaults/:reference/change-password` | `currentPassword,newPassword,confirmPassword`, optional `autoLockMinutes` | 200 `S{vault:Vault}` (locked) | 404; 409 legacy; 400 validation; 401 wrong; 429 |
| POST | `/api/vaults/:reference/reset-password` | `accountPassword,newPassword,confirmPassword`, optional `autoLockMinutes` | 200 `S{vault:Vault}` (locked) | 404; 400 validation; 401 wrong; 429 |
| PATCH | `/api/vaults/:reference` | At least one of `name,description,password`; must be unlocked; password only for legacy Vault | 200 `S{vault:Vault}` | 404; 423; 400 validators |
| DELETE | `/api/vaults/:reference` | Must be unlocked | 204 empty | 404; 423 |
| POST | `/api/vaults/:reference/assets` | `assetIds` array; must be unlocked | 200 `S{vault:Vault}` | 404; 423; 400 ids; 409 membership |
| DELETE | `/api/vaults/:reference/assets/:assetId` | Must be unlocked | 200 `S{vault:Vault}` | 404; 423; 400 id |

`Vault = {reference,name,description,assetCount,passwordProtected,isLocked,unlockExpiresAt,autoLockMinutes,createdAt,updatedAt,assets:VaultAsset[]}`; never internal Vault/user id or password hash. `VaultAsset = {id,title,description,category,fileName,fileSize,mimeType,status,createdAt,width,height,hasHash,hasMetadata,addedAt,reference,contentUrl,vaultProtection:{passwordProtected:true,isLocked:false}}`. No raw hashes or updatedAt here. Locked Vault returns `assets:[]`; unlocked list previews at most three, detail returns all, sorted `added_at DESC,asset id DESC`, with current asset ownership filtering. Vault list sorted `updated_at DESC,id DESC`. Counts count memberships; do not silently substitute preview length. `VaultStats = {totalVaults,organizedAssets,totalAssets,unorganizedAssets}`; organizedAssets counts distinct memberships in user's Vaults, unorganized is max(0,totalAssets-organizedAssets).

Reference normalization trims/uppercases and requires `^VT-[A-F0-9]{6}$`; invalid, absent and foreign references all yield 404 `Vault not found`. New references use three random bytes, at most four collision retries; failure is 500 `Unable to allocate a Vault reference`.

Validators and messages (400 unless marked): name trimmed/nonempty/max80 (`Vault name is required`, `Vault name must be 80 characters or fewer`); optional trimmed description max500 (`Description must be 500 characters or fewer`); password is **8–72 UTF-8 bytes** (messages `Vault password must be at least 8 characters`, `Vault password must be 72 bytes or fewer`); confirmation mismatch `New Vault passwords do not match`; autoLockMinutes is numeric 5/10/30, default10 (`Auto-lock duration must be 5, 10, or 30 minutes`). PATCH with none of name/description/password: `Provide a name or description to update`; password on protected Vault: `Vault password changes are not supported`. PATCH autoLockMinutes alone is not supported.

Membership input: nonempty array (`Select at least one asset`), max50 before deduplication (`Add no more than 50 assets at once`), each Number-convertible positive integer (`Asset IDs must be positive integers`), no duplicate normalized ids (`Duplicate asset IDs are not allowed`). All assets must be currently owned: 404 `One or more assets were not found`. Existing membership: 409 `One or more assets are already in this Vault`; insert constraint fallback 409 `Unable to add duplicate or unavailable assets`. Remove bad id: 400 `Asset ID must be a positive integer`; absent membership: 404 `Asset is not in this Vault`.

Security errors: 423 `Vault is locked` for locked mutations; 423 `Protected by Vault — unlock every protecting Vault to access` for asset/marketplace protection. Unlock legacy: 409 `This legacy Vault does not have password protection configured`; change-password legacy: 409 `Set an initial password through Edit Vault`. Wrong unlock: 401 `Incorrect Vault password`; wrong change: 401 `Current Vault password is incorrect`; wrong reset: 401 `Account password is incorrect`. Threshold failure/block: 429 `Too many Vault password attempts. Try again later.`; reset's threshold-crossing failure instead says `Too many password attempts. Try again later.` (pre-existing block still uses the Vault wording).

### 3.4 Global verification and report history

Source: [verification service](../server/src/services/verification/verificationService.js), [repository](../server/src/repositories/verificationRepository.js). Bearer only; global matching intentionally spans owners, but stored history is requester-scoped.

| Method | Exact path | Input | Success | Route errors |
| --- | --- | --- | --- | --- |
| POST | `/api/verifications` | Multipart `file`; no asset id | 201 `S{verification:DetailedReport}` | 400 `Comparison image is required`; upload/hash/persistence errors |
| GET | `/api/verifications` | None | 200 `S{verifications:SummaryReport[]}` | Common errors |
| GET | `/api/verifications/:reference` | Trim/uppercase reference | 200 `S{verification:DetailedReport}` | 404 `Verification report not found` |

New global report: `{reference,result:"matches_found"|"no_match",createdAt,matches:GlobalMatch[],thresholds:{strong,possible,maxResults,hashBits},nearestDistance,candidateCount,comparison?}`. Detailed responses add `comparison:{fileName,mimeType,fileSize,width,height}`; lists omit it. Name is basename(originalname || filename || "comparison-image"), stripped of control chars U+0000–001F/U+007F, limited to 180 JS characters, with fallback `comparison-image`.

`GlobalMatch = {rank,matchType:"exact"|"strong_visual"|"possible_visual",assetReference,ownerReference,ownerIsCurrentUser,sha256Match,distance,hashBits,registeredAt,asset?}`. Optional `asset:{id,title,mimeType,contentUrl}` only appears in detailed results when the stored match owner is the requester, the asset is still currently owned, and all protecting Vaults are unlocked. Lists never add asset detail. Cross-owner evidence omits titles, filenames, raw hashes, metadata, emails, ids and paths; public asset references still encode asset ids by design.

Candidates require both non-null SHA and pHash; all statuses participate. Compute bit distance; exact SHA forces distance0 even if stored pHash is malformed. Skip nonexact malformed pHash rows. Sort exact SHA first, then distance ascending, then asset id ascending; retain only exact or distance<=possible, then first **five**, assigning 1-based rank. Strong<=6, possible<=12 by default. Non-SHA distance0 is `strong_visual`, not ownership check's `identical`. `candidateCount` counts source candidates including invalid ones; nearestDistance is null when any meaningful result exists, otherwise first ranked distance or null. Metadata parse failure is tolerated using `{width:null,height:null,metadataJson:{}}`, unlike asset upload/check. Always unlink temporary file in `finally`, including report persistence failures.

Persist `verification_type='global_image_search'`, requester `user_id`, first matched asset or null, first-match sha256 flag 0/1, `similarity_score=NULL`, status, full internal report JSON (including internal match ids). API history selects only `image_comparison` and `global_image_search`, sorted `created_at DESC,id DESC`; detail searches only that caller's rows for derived reference.

Legacy report shape: `{reference,result:storedStatus,createdAt,registeredAsset:{reference,...conditionalDetails},fingerprints:storedFingerprintsOrEmpty}`; detail adds `comparison` if present, `metadataDifferences:[]` fallback, `warnings:[]` fallback. Unlocked/current-owned detail adds `id,title,fileName,contentUrl` then spreads stored `registeredAsset`. Preserve readable legacy report JSON; malformed JSON parses to `{}`. Public references are recomputed, so changing the HMAC secret changes old report URLs. Global match owner labels are historical snapshots, while optional asset detail also checks current ownership; labels are not fully recomputed after a sale.

### 3.5 Documents and OCR

Source: [document service](../server/src/services/document/documentService.js), [repository](../server/src/repositories/documentRepository.js), [OCR service](../server/src/services/ocr/ocrService.js). All endpoints Bearer and owner-scoped.

| Method | Exact path | Input | Success | Route errors |
| --- | --- | --- | --- | --- |
| POST | `/api/documents` | Multipart `file` | 201 `S{message:"Document uploaded successfully",document:DetailedDocument}` | 400 missing/type; 413 size; storage errors; OCR failure is represented in success body |
| GET | `/api/documents` | Query `search?,type?,ocrStatus?` | 200 `S{documents:Document[]}` | 400 filters |
| GET | `/api/documents/:id` | Positive integer id | 200 `S{document:DetailedDocument}` | 404 |
| GET | `/api/documents/:id/content` | id | 200 bytes | 404 document/file |
| GET | `/api/documents/:id/ocr` | id | 200 `S{ocr:{status,extractedText,processedAt,error,confidence}}` | 404 |
| POST | `/api/documents/:id/ocr` | id; no body required | 200 `S{message:"OCR processing finished",document:DetailedDocument}` | 404; OCR failure persisted in body |
| DELETE | `/api/documents/:id` | id | 204 empty | 404 |

`Document = {id,reference,originalName,mimeType,fileSize,sha256,pageCount,language,ocrStatus,ocrProcessedAt,createdAt,contentUrl}`; reference `DOC-` plus decimal id padded to six digits. DetailedDocument adds `{extractedText,ocrError,confidence}` with text fallback `""`, error fallback null. Do not expose storedName, filePath, ownerId, or full extracted text in list results. With nonempty search only, list adds `{matchedOcrText:boolean,ocrSnippet:string|null}`. Without search these keys are omitted (undefined is dropped by JSON). Original filename uses basename, control-character stripping and max180, fallback `document`.

Bad/foreign/missing id: 404 `Document not found`; missing file: 400 `Document file is required`. Query arrays rejected: `Invalid search filter`, `Invalid type filter`, `Invalid ocrStatus filter` (400). Trim search; max200 (`Search must be 200 characters or fewer`). Type lowercased and one of blank/pdf/image (`Invalid document type filter`). ocrStatus lowercased blank/pending/processing/completed/failed (`Invalid OCR status filter`). Search uses SQLite `instr(lower(...),lower(?))` on filename OR OCR text, treating `%` and `_` literally; SQLite lower's Unicode behavior must not be replaced with a broader Java case-fold. Snippet uses SQL substring of max160 characters, starting max(1,matchPosition-50), then JS trim. Combine filters with AND and owner condition. Newest `created_at DESC,id DESC` first.

Upload stores raw-file SHA-256, creates a pending document then synchronously performs OCR before returning. Processing changes status to processing, clears error, then atomically upserts the unique OCR result and sets completed/pageCount/language/processedAt. OCR errors are caught and persist failed status with `Text extraction failed. You can retry OCR.` unless message matches `limited to \d+ pages`; preserve `Scanned PDF OCR is limited to 10 pages`. Failed uploads remain retrievable with original bytes (201); retry returns 200 even when OCR fails. Retry failure can leave older extracted text/confidence in place. Only pre-document creation failures delete uploaded file. Delete cascades OCR result and attempts to remove file; ENOENT and other unlink failures are swallowed.

Images validate PNG eight-byte/JPEG three-byte signatures, then Tesseract English worker (OEM argument1), read-only bundled language cache; trim returned text, confidence from engine, pageCount1, language `eng`; terminate worker in finally. PDFs run `pdfinfo`, then `pdftotext -layout <file> -`; trimmed embedded text length>=10 bypasses OCR (confidence null) regardless of page count. Otherwise scanned OCR rejects known counts>10, rasterizes with `pdftoppm -png -r 150`, sorts pages numerically, OCRs each sequentially, joins nonempty texts with two newlines, averages finite Number-converted confidences (null converts to0). Unknown page count does not trigger the limit. Each subprocess has 120000ms timeout and 25MiB maxBuffer; the temporary `vaultchain-pdf-ocr-*` OS directory is removed in finally. `semantic_hash` is unused.

### 3.6 Wallet and marketplace

Source: [wallet service](../server/src/services/wallet/walletService.js), [marketplace service](../server/src/services/marketplace/marketplaceService.js), [purchase transaction](../server/src/repositories/marketplaceRepository.js). All Bearer; ownership rules indicated.

| Method | Exact path | Input / access | Success | Route errors |
| --- | --- | --- | --- | --- |
| GET | `/api/wallet` | Own wallet | 200 `S{wallet:{balance,currency:"Credits"}}` | 404 wallet |
| GET | `/api/wallet/transactions` | Own wallet | 200 `S{transactions:WalletTransaction[]}` | 404 wallet |
| POST | `/api/wallet/transactions` | JSON `type,amount,description?,referenceId?` | 201 `S{message:"Transaction recorded successfully",wallet:{balance,currency:"Credits"},transaction:WalletTransaction}` | 400 type/amount/balance; 404 wallet |
| POST | `/api/marketplace/listings` | JSON `assetId,title,price,description?`; owned/unlocked asset | 201 `S{message:"Listing created successfully",listing:Listing}` | 400 fields/price; 404 asset; 423; 409 duplicate; 503 reference |
| GET | `/api/marketplace/listings` | No filters | 200 `S{listings:Listing[]}` | Common errors |
| GET | `/api/marketplace/listings/:reference` | Any authenticated requester | 200 `S{listing:Listing}` | 404 |
| GET | `/api/marketplace/listings/:reference/content` | Active listing with current seller ownership; Vault rules | 200 bytes | 404 listing/content; 423 |
| PATCH | `/api/marketplace/listings/:reference` | Seller only, active; JSON optional `price,title,description` | 200 `S{message:"Listing updated successfully",listing:Listing}` | 404; 409 inactive/race; 400 fields |
| DELETE | `/api/marketplace/listings/:reference` | Seller only, active | 200 `S{message:"Listing cancelled successfully",listing:Listing}` | 404; 409 inactive/race |
| POST | `/api/marketplace/listings/:reference/purchase` | No body; buyer cannot be seller | 200 `S{message:"Purchase completed successfully",receipt:Receipt}` | 404 listing/wallet; 409 unavailable/owner/self; 400 funds |

`WalletTransaction = {id,walletId,type,amount,description,referenceId,createdAt}` sorted `created_at DESC,id DESC`. Manual type exactly `deposit`/`withdrawal`; `sale` and `purchase` cannot be forged. Errors: 400 `Type must be one of deposit or withdrawal`, `Amount must be a positive number`, `Insufficient wallet balance`; 404 `Wallet not found`. Number(amount) must be finite and>0; no two-decimal restriction or upper cap on manual amount. Manual balance arithmetic is unrounded, and balance update+ledger insert share one transaction. Transaction amounts remain positive; type determines sign. Description/referenceId falsy values become null. Currency intentionally differs from marketplace.

`Listing = {reference,title,description,price,currency:"VaultChain Credits",status,createdAt,soldAt,seller:{reference,isCurrentUser},asset:{id,reference,title,category,mimeType,fileSize,width,height,previewAvailable,contentUrl,passwordProtected,isLocked}}`. Lists include **all statuses/all sellers**, newest `created_at DESC,id DESC`. Asset id is visible only to original seller; foreign id null. Active/current-seller-owned listings evaluate seller Vault protection, but non-seller requesters use no seller token; an owner's unlocked session cannot unlock buyer preview. Locked category/mimeType/fileSize null. Preview requires active status and not locked; width/height/contentUrl null otherwise. Listing title and asset title still appear. Content separately requires active AND current seller ownership. Sold/cancelled DTOs skip protection check and may show category/type/size, but no preview. No private name/email/file path/filename or raw hashes.

Reference is trim+uppercase `^ML-[A-F0-9]{6}$`; invalid/absent is 404 `Listing not found`. Create generates random3-byte hex references, eight attempts then 503 `Could not allocate a listing reference`. `assetId` Number-converts to positive integer or400 `assetId is required`. Title trim nonempty max120 (`Title is required`, `Title must be 120 characters or fewer`); description optional trim max1000 (`Description must be 1000 characters or fewer`). Price finite, >= setting `minimum_listing_price` (fallback1), <=1e9 or400 `Price must be at least {minimum}`; cent precision tolerance `abs(price*100-round(price*100))<=1e-8` or400 `Price may have at most two decimal places`.

Duplicate active listing: 409 `This asset already has an active listing`; create catches any SQLITE_CONSTRAINT with that message. PATCH null/omitted fields preserve previous values via COALESCE; blank optional description normalizes null and therefore **cannot clear** an existing description. Empty PATCH is allowed. No extra Vault unlock check on update/cancel/purchase. Errors409: `Only active listings can be updated`, `Only active listings can be cancelled`, `Listing is no longer active`; content404 `Listing content not found`.

`Receipt = {transactionReference,listingReference,asset:{reference,title},previousOwner,newOwner,price,platformFee,sellerAmount,currency:"VaultChain Credits",completedAt,buyerBalance,sellerBalance}`. Owner fields are HMAC pseudonyms. Current implementation exposes sellerBalance to buyer; treat as a recorded privacy compatibility issue, not an omitted field. `OwnershipHistory = {transactionReference,listingReference,price,currency:"VaultChain Credits",transferType,transferredAt,previousOwner,newOwner}`; nullable historical owners map null, sorted transferred_at DESC,id DESC, available to current asset owner without Vault unlock.

**Atomic sale invariant:** one serialized `BEGIN IMMEDIATE TRANSACTION` validates listing existence404, active status409 `This listing is no longer available`, seller ownership409 `The seller no longer owns this asset`, self-purchase409 `You cannot purchase your own listing`, wallets404, funds400 `Insufficient VaultChain Credits`; reads current commission setting (default .05, clamps numeric value 0..1); rounds fee with JS `Math.round(price*rate*100)/100` and seller payout with `Math.round((price-fee)*100)/100`. Guarded buyer debit and SQLite ROUND(balance±amount,2), seller credit, guarded asset owner change, guarded sold/buyer/sold_at update, removal from all seller-owned Vaults, ownership_history `marketplace_sale`, marketplace_transactions `completed`, and two wallet ledger records must commit or roll back together. File path/content/hash/metadata and requester-owned reports are unchanged. Same `TX-` random3-byte uppercase hex reference links all ledgers. Buyer description `Marketplace purchase: {asset title}`; seller description `Marketplace sale payout after {rounded percent}% platform fee: {asset title}`. Race allows one winner, other409. Reference collision currently rolls back (no TX retry). Receipt rereads occur **after COMMIT**, so an error at that point cannot undo the completed sale; add an explicit parity/failure test rather than claiming all response failures roll back.

### 3.7 Dashboard

| Method | Exact path | Auth/input | Success | Route errors |
| --- | --- | --- | --- | --- |
| GET | `/api/dashboard/summary` | Bearer; no input | 200 `S{summary:DashboardSummary}` | Common/DB errors |

[Repository](../server/src/repositories/dashboardRepository.js) is the complete implementation; service just delegates.

`DashboardSummary = {totalAssets,totalDocuments,totalVerificationReports,totalVaults,totalOrganizedAssets,totalVaultItems,activeListings,walletBalance,recentAssets,recentDocuments,recentActivity}`. All numeric counts/balance default0; totalVaultItems aliases organized distinct asset count. Reports count all requester-owned report types, unlike verification history's type filter. activeListings is own seller active count. recentAssets max5 `{id,title,category,mimeType,createdAt}`; recentDocuments max5 `{id,originalName,mimeType,ocrStatus,createdAt}`; both date DESC,id DESC. Recent activity max8 across current-owned asset uploads, requester verifications, own purchase/sale wallet entries, own document uploads; sort date DESC, source sequence/id DESC. No deposit/withdrawal activity. `{type,title,amount,reference,status,assetId,documentId,createdAt}` with null unused values. Verification title `Image verification` and reference null; asset/document references decimal padded. After transfer the asset-upload activity follows current ownership rather than original creator. No Vault redaction is applied to these summary titles.

### 3.8 Admin control center

Sources: [admin routes](../server/src/routes/adminRoutes.js), [admin repository](../server/src/repositories/adminRepository.js). **Admin success envelopes do not include `success`**. Every route first requires Bearer plus one of SUPER_ADMIN/MODERATOR/FINANCE_ADMIN/VERIFICATION_ADMIN. In the table `ALL` means those four, `S` SUPER_ADMIN, `M` MODERATOR, `F` FINANCE_ADMIN, `V` VERIFICATION_ADMIN. Date input `Q` is optional `range,from,to` query. No general user access.

| Method | Exact path | Roles | Input | Success | Route errors |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/overview` | ALL | Q | 200 `{overview:Overview}` | Common |
| GET | `/api/admin/revenue` | S,F | Q | 200 `{revenue:Revenue}` | Common |
| GET | `/api/admin/marketplace` | ALL | Q | 200 `{marketplace:AdminMarketplace}` | Common |
| GET | `/api/admin/transactions` | S,F | Q | 200 `{transactions:AdminTransactions}` | Common |
| GET | `/api/admin/users` | S | Q | 200 `{users:AdminUsers}` | Common |
| GET | `/api/admin/assets` | S,M,V | Q | 200 `{assets:AdminAssets}` | Common |
| GET | `/api/admin/verification` | S,M,V | Q | 200 `{verification:AdminVerification}` | Common |
| GET | `/api/admin/analytics` | ALL | Q | 200 `{analytics:Analytics}` | Common |
| GET | `/api/admin/security` | S,M | None | 200 `{security:Security}` | Common |
| GET | `/api/admin/notifications` | ALL | Own user id | 200 `{notifications:Notification[]}` | Common |
| PATCH | `/api/admin/notifications/read` | ALL | No body | 204 empty | Common |
| GET | `/api/admin/logs` | S | None | 200 `{logs:ActivityLog[]}` | Common |
| GET | `/api/admin/settings` | S | None | 200 `{settings:{[key]:{value,updatedAt}}}` | Common |
| PATCH | `/api/admin/settings/marketplace` | S | JSON `commissionPercentage,minimumListingPrice` | 200 `{marketplace:{commissionPercentage,minimumListingPrice}}` | 400 limits |
| PATCH | `/api/admin/users/:id` | S | JSON optional `role,status` | 200 `{user:{id,full_name,email,role,status}}` | 400 values/self-demotion; 404 user |
| PATCH | `/api/admin/listings/:id` | S,M | JSON `status` | 200 `{listing:{id,public_reference,status}}` | 400 status; 404 absent/sold; DB constraint500 possible |
| PATCH | `/api/admin/assets/:id` | S,M,V | JSON `status` | 200 `{asset:{id,title,status}}` | 400 status; 404 asset |

Admin DTO shapes (SQL field names are intentional):

- `Range={from,to,previousFrom,previousTo,resolution}`. Timeline points always `{key,label,...series}`.
- `Overview={range,totals:{users,assets,verifiedAssets,verificationRate},period:{newUsers,newAssets,transactions,grossVolume,marketplaceRevenue},changes:{users,assets,transactions,revenue},trend:[{key,label,revenue,transactions}],recentTransactions:AdminTransactionRow[<=4],health:{databaseLatencyMs,processUptimeSeconds,database,activeListings,verificationQueue,flaggedAccounts,paymentSuccess},securityEvents:SecurityEvent[<=3],commissionRate}`. SecurityEvents forced[] for F,V; other data is still returned. Totals all-time; period counts in range. VerifiedAssets counts distinct nonnull report asset ids; verificationQueue assets with no reports; flaggedAccounts review users. Revenue sums completed platform fees; payment success completed/all period transactions.
- `AdminTransactions={range,summary:{total,gross_volume,completed,pending,refunded,refunded_value,successRate},rows:AdminTransactionRow[]}`. Row `{transaction_id,asset,seller,buyer,sale_amount,platform_fee,seller_amount,status,created_at}`; names are full names. Gross volume includes every status; refunded_value refunded only; completed/pending/refunded can be null on empty data because SQL SUM is not coalesced. Rows newest first, no limit.
- `Revenue={range,summary:{totalRevenue,marketplaceFees,pendingPayments,refunds,completedTransactions,totalTransactions,commissionRate},changes:{revenue,transactions},sources:[{name:"Marketplace commission",amount,percentage:100}],trend:[{key,label,revenue,transactions}],transactions:AdminTransactionRow[<=5]}`. Empty revenue sources[]; pending/refunds are corresponding fee sums, not sale gross. Rate output is percentage, setting stored fraction.
- `AdminMarketplace={range,summary:{totalListings,activeListings,soldAssets,averageSellingPrice,commissionEarned},rows:[{id,public_reference,asset,owner,price,status,created_at,sold_at,platform_fee}]}`. Listing creation date controls listing inclusion; commission uses sale date range. Average only sold prices. Row fee uses `persisted_fee || price*current_rate`, so actual zero fee falls through.
- `AdminUsers={range,summary:{total,active,admins,review,new_users},rows:[{id,full_name,email,role,status,created_at,assets,transactions,revenue_generated}]}`. All users always listed, not range-filtered; new_users alone uses range. Counts/revenue are all-time, revenue_generated sums seller fees without status filter. Raw SUM fields may be null.
- `AdminAssets={range,summary:{total,verified,suspicious,pending},rows:[{id,title,category,asset_status,created_at,owner,verification_score,marketplace_status,has_verification}]}`. Range on asset created_at. Verified means any report; suspicious means lower(status) review/suspicious. Score MAX(exact?100:similarity_score), not global JSON score. Marketplace status latest listing created_at; flag is integer0/1.
- `AdminVerification={range,summary:{total,successful,duplicates,rejected,scored},trend:[{key,label,total,successful,rate}],confidence:[{range,count}]}`. Successful = status not failed/rejected (including no_match). Exact flag scores100; else finite nonnull similarity_score; else first global JSON match -> max(0,round((1-distance/hashBits)*1000)/10); otherwise null. Confidence buckets `<80%`, `80–90%`, `90–95%`, `95–100%`, thresholds 80/90/95. No raw reports returned.
- `Analytics={range,summary:{monthlyActiveUsers,newAssets,grossVolume,activeRegions:null},timeline:[{key,label,newUsers,newAssets,transactions,revenue,users,assets,volume}],performance:[{metric,value}],regions:[],geographyAvailable:false}`. monthlyActiveUsers is distinct users with asset/verification/seller/buyer activity in the **selected range**, not necessarily month; marketplace activity includes all statuses. Timeline cumulative users/assets starts with rows before range; transactions/revenue/volume only completed. Performance metrics `Verification`, `Sell-through` are all-time; `Payment success` in range; `Active users` period active / all users, rounded integer percentages.
- `Security={summary:{securityScore,blockedThreats,activeSessions,riskyAccounts},health:{databaseLatencyMs,processUptimeSeconds,database:"operational"},events:SecurityEvent[<=50],controls:[{name,detail,status}],riskySessions:[]}`. Score max(0,100-blocked*5-review*2). Attempts (latest50) and selected admin logs merge/sort by event date. `SecurityEvent={time,title,detail,level,type}`; attempt titles `Vault access temporarily blocked` or `Failed Vault unlock attempts`, detail `{count} attempt[s] · {email}`, levels high/medium, type `Vault access`. Selected logs only `updated_user_access`/`changed_commission_rate`, underscores replaced with spaces, detail `{admin} · {target_type||system} {target_id||''}`, level low/type Admin. Current settings action is `updated_marketplace_settings`, so it is not selected here. Controls: JWT authentication/Required on protected API routes/Active; Admin audit logging/Privileged mutations are recorded/Active; Vault password protection/Rate-limited bcrypt verification/Active; Database at-rest encryption/No encryption provider configured/Not configured.
- `Notification={id,title,message,is_read,created_at}` newest20 for requester; mark-read updates all their notifications, not just displayed20. `ActivityLog={id,admin,action,target_type,target_id,details_json,ip_address,created_at}` newest250; details_json is a string, not parsed object.

Admin mutations: role uppercase from truthy body value; valid five roles. Status lowercase from truthy body value, valid active/suspended/review. Empty patch returns existing user and still audits. Self-target cannot set status suspended or role USER; self-change to another admin role is allowed. 400 messages: `Invalid role`, `Invalid status`, `You cannot remove your own admin access`. Absent user404 `User not found`. Listing allowed active/cancelled (`Invalid listing status`400); update only existing active/cancelled rows (sold ->404 `Listing not found`). Active partial unique constraint still applies. Asset allowed active/review/suspicious/suspended (`Invalid asset status`400); absent404 `Asset not found`.

Settings Number-coerce both inputs: commission finite 0..20 inclusive (`Commission must be between 0% and 20%`400), minimum finite >0 and <=1e9 (`Minimum listing price must be a positive number`400). Persist string fraction and string minimum. No cent-precision validation for settings minimum. Audit actions `updated_marketplace_settings`, `updated_user_access`, `updated_listing_status`, `updated_asset_status`; target types platform_settings/user/listing/asset; listing target is public reference. Include req.ip and JSON details. Updates/settings and audit writes are **not** one transaction in the Node implementation.

Date-range behavior: UTC. Valid `to` overrides now; a date-only `to` becomes23:59:59.999 then serializes to SQL second precision23:59:59. Valid `from` overrides presets and starts day resolution. Otherwise today starts midnight/hour resolution, 7d starts six days before end-day midnight, 1y starts first-of-month eleven months earlier/month resolution; everything else (including unknown/custom without valid from) is 30d starting29 days earlier/day resolution. Invalid dates silently fall back. More than62 elapsed days changes resolution to month. Reversed ranges are not rejected. Prior window duration=max(1hour,end-start), previousFrom=start-duration, previousTo=start-1ms then seconds truncation. SQL boundaries inclusive. Zero-fill up to400 buckets, cursor at month/day start or exact range.from for hours. Keys hour `YYYY-MM-DD HH`, day `YYYY-MM-DD`, month `YYYY-MM`; labels English UTC hour/month-day/short-month-two-digit-year. Percent change previous0 => current nonzero100 else0; otherwise round(((current-previous)/abs(previous))*1000)/10. Preserve empty/NULL aggregate quirks.

## 4. Configuration and storage contract

| Variable | Default and interpretation |
| --- | --- |
| PORT | `3000`; Node takes env value directly |
| DATABASE_PATH | process env, then manual `server/.env` DATABASE_PATH, then `server/src/database/vaultchain.sqlite`; relative configured paths resolve against process cwd |
| JWT_SECRET | `vaultchain-development-secret`; string bytes used for HMAC, not implicit base64 decoding |
| JWT_EXPIRES_IN | `7d`; passed unchanged to jsonwebtoken expiresIn (duration strings must retain Node semantics, including unitless strings) |
| PUBLIC_ID_SECRET | PUBLIC_ID_SECRET, then JWT_SECRET, then development secret |
| UPLOAD_DIRECTORY | absolute module-relative `server/src/uploads` |
| CHECK_UPLOAD_DIRECTORY | absolute module-relative `server/src/temp` |
| DOCUMENT_UPLOAD_DIRECTORY | absolute module-relative `server/src/documents` |
| PHASH_STRONG_MATCH_MAX | parseInt base10, nonnegative integer or6 |
| PHASH_POSSIBLE_MATCH_MAX | parseInt base10, nonnegative integer or12, raised to at least strong threshold |
| VAULT_UNLOCK_TTL_SECONDS | per-Vault autoLockMinutes*60, fallback600; positive finite override floored and capped86400 |
| VAULT_UNLOCK_MAX_ATTEMPTS | 5; positive finite Number, floored/capped100 |
| VAULT_UNLOCK_WINDOW_SECONDS | 900; positive finite Number, floored/capped86400 |
| VAULT_UNLOCK_BLOCK_SECONDS | 900; positive finite Number, floored/capped86400 |
| VITE_API_URL | frontend `http://localhost:3000/api` |

Auth secret/expiry, paths and pHash thresholds are captured at module load; Vault rate/TTL settings and public-id secret are read on use. Positive fractional Vault overrides below1 floor to0 (not fallback) in current code. Main app calls dotenv with cwd defaults before imports. Database also has its own simple server/.env parser: trim key/value, ignore blank/#, split first `=`, no quote removal. No env file was present to establish deployed values. Do not put real secrets in migration docs/fixtures. No OCR language/commission environment variable is read; commission/minimum come from SQLite settings.

All uploads use one multipart part named `file`, max **20*1024*1024 =20,971,520 bytes per file**, generated name `{Date.now()}-{Math.round(Math.random()*1e9)}{lowercase original extension}`. Directories recursively created at middleware import. Both extension and client-declared MIME must belong to allowlists (not necessarily matching each other): assets/checks/verifications `.jpg,.jpeg,.png,.webp` AND `image/jpeg,image/png,image/webp`; documents `.pdf,.jpg,.jpeg,.png` AND `application/pdf,image/jpeg,image/png`. Unsupported asset400 `Only jpg, jpeg, png, and webp files are allowed`; unsupported document400 `Only PDF, JPG, JPEG, and PNG documents are allowed`. Limit413 `File size exceeds the 20 MB limit`; other Multer errors400 with original message (e.g. unexpected field). Authentication precedes upload writing.

Asset/marketplace content path is resolved from configured upload directory + basename(fileName), **not stored file_path**. Documents likewise use document directory + basename(storedName). Preserve this when pointing Java at copies of existing storage; changing default to spring-server-local empty directories would break every old URL. Content type is stored MIME (assets/marketplace fallback application/octet-stream), Cache-Control `private, max-age=300`; documents additionally Content-Disposition `inline`. Express sendFile supplies further transport headers/statuses. No unauthenticated upload URLs. Java must map missing-file exceptions to JSON errors rather than default HTML and capture Node's actual filesystem messages if required.

Hash utility errors:400 `Missing file path`;404 `Uploaded file not found`;500 `Failed to read uploaded file`; metadata parse500 `Failed to extract image metadata`; pHash unsupported415 `Unsupported image format` (normally blocked earlier by upload400), decoder/hash failure `Failed to generate perceptual hash` with underlying status or500. Raw document SHA helper only customizes missing path/ENOENT; other failures propagate.

## 5. Authentication and Vault persistence rules

JWT creation has `id,email,role,status,jti` (random UUID) plus library `iat,exp`; HMAC signing uses jsonwebtoken default HS256. Verification currently calls jwt.verify without an explicit algorithm whitelist; a strict HS256-only migration must explicitly account for any existing HS384/HS512 HMAC tokens the Node verifier would accept. Token bytes, UTF-8 secret interpretation, expiry boundary and duration parsing need interoperability fixtures. Every authenticated request reloads user by decoded id and replaces email/role/status from DB; no stale-authority trust. Fingerprint is lowercase SHA-256 of exact bearer token string. Distinct logins have distinct jti even in same second.

Account BCrypt cost10; Vault BCrypt cost12. Existing `$2a$`/`$2b$` (and compatibility with `$2y$` fixtures) must verify correctly in Java; account minimum is JS string length8, no max72-byte check, while Vault enforces bytes8..72. Include multibyte passwords and BCrypt truncation fixtures; do not silently rehash/reset existing passwords. Password change replaces hash but does not revoke account JWTs. Logout only deletes Vault unlock sessions for `(user_id,token_fingerprint)`; the JWT remains accepted for ordinary requests. Another JWT's Vault grants survive logout.

Vault session PK `(vault_id,token_fingerprint)`, lookup includes user id; expiry is strictly greater than current time. Unprotected legacy Vault is accessible without grant; protected Vault without fingerprint is locked. Unlock grants a fixed expiration, no sliding extension on reads. Grant deletes globally expired sessions then upserts. Manual lock removes only this token's grant. Change/reset revokes **all tokens for that Vault** and returns locked; changing account password does not do so. Legacy PATCH can set initial Vault password; no automatic unlock follows. Vaults are organizational access controls, not file encryption.

Attempt state keyed `(vault_id,user_id)`, shared across tokens and unlock/change/reset. Check blocked_until strictly future first; failed attempt starts/reset window if window_started+windowSeconds<=now, increments otherwise; failure at maxAttempts already returns429. Block lasts configured duration; after block expires correct password succeeds even if attempt window remains, clearing attempts. New wrong attempt in same window may block again. No Retry-After header. Validation of new passwords precedes attempt recording in change/reset. Grant/password update/revocation and rate read/increment are not single atomic sequences in Node; race behavior needs explicit testing in Java.

Protected asset access requires **every** password-protected Vault owned by current owner to have an unexpired grant for that token. Earliest active expiry is returned only if none are locked. Removing membership never deletes asset/hash/report/file; deleting Vault cascades memberships/grants/attempts only. Adding batch memberships is serialized `BEGIN IMMEDIATE`, all-or-nothing, updates Vault updated_at. Asset deletion at DB level cascades memberships; there is no public asset delete route.

Known response inconsistency: an unlocked Vault's embedded asset DTO does not re-evaluate other protecting Vaults; it hardcodes isLocked=false and exposes dimensions/content URL even if another Vault locks that asset. The content/hash/metadata endpoints still deny. This must be a documented privacy decision and test, not accidentally described as fully redacted parity.

## 6. Fingerprint and metadata compatibility: release blockers

The exact algorithm at the application boundary is:

```text
asset SHA256 = lowercaseHex(SHA256(fileBytes || UTF8(JSON.stringify({
  metadata: recursivelyNormalizedMetadata,
  asset: recursivelyNormalizedAssetData
}))))
```

All active asset/check/verification callers leave assetData as `{}`. The outer key order is metadata then asset. Recursive normalization turns Date into ISO strings, sorts object keys, keeps array order, omits undefined object values; JSON.stringify number/string/Unicode/undefined-array handling is part of the byte contract. Java MessageDigest alone does not solve this. Plain file SHA-256 is used only for **documents**.

Metadata extraction calls exifr.parse with `{gps:true,tiff:true,ifd0:true,exif:true,xmp:true}`. Null result becomes `{}`. Width precedence ImageWidth/ExifImageWidth/PixelXDimension; height ImageHeight/ExifImageHeight/PixelYDimension; uses JS truthy OR, not decoder dimensions. pixelCount if both truthy, else null. Camera derives lowercase `make,model`; location requires numeric `latitude,longitude` and lowercase `latitudeRef,longitudeRef`, directions S/W force negative and outputs `lat,lon`; capture date lowercase `dateTimeOriginal || createDate || modifyDate`. Do not silently correct these casing choices to conventional EXIF tag names. Raw metadataJson spreads **all parsed fields**, then pixelCount/patterns. Patterns in order: resolution-available, gps-present (both coords nonnull), camera-present, capture-date-present, orientation-present (capital Orientation nonnull). This may report GPS presence while location is null. Repository metadata reads JSON.parse without malformed-JSON fallback, derives missing pixelCount, defaults patterns[]. A Java library returning more complete dimensions/camera data changes existing SHA matching.

pHash application call is exactly `imageHash({data:fileBuffer,ext:mimeType},16,true,callback)` from image-hash5.3.2, output lowercased; tests establish a256-bit/64-hex result. Its dependency chain includes jpeg-js/pngjs/@cwasm/webp. **The dependency implementation is absent from this checkout; resize kernel, grayscale/alpha rules, transform/threshold, orientation handling, floating-point precision and bit packing have not been verified. Do not assert that a generic Java “pHash” or64-bit DCT library is equivalent.** Before selecting the Java algorithm, inspect the lockfile-resolved library source in an isolated Node installation and save exact input/output fixtures. WebP is accepted at middleware and decoder boundary but not directly covered by existing tests.

Hamming distance normalizes nonempty hex strings case-insensitively and sums nibble XOR popcounts (0 vs f =4 bits); equal lengths required. Helpers throw `Perceptual hash must be a non-empty hexadecimal string` / `Perceptual hashes must have equal lengths`; candidate matching skips bad stored hashes. No requirement that stored hash be64 hex digits in comparison helper, only equal lengths to target. Thresholds are bits, not percent or count of differing hex characters.

Metadata comparison helper (still tested, not used by current global matcher) yields nine ordered fields dimensions/aspectRatio/format/fileSize/camera/dateTaken/orientation/software/gps. Dimensions use multiplication sign ` × `, ratio fixed4 decimals, fileSize finite number converted to string, date normalized, orientation first Orientation/orientation, software first Software/software/CreatorTool; GPS only Present/Not present, never coordinates. Comparison statuses same/changed/added/removed/unavailable distinguish nulls. Preserve legacy report readability and helper unit tests.

Required fixtures: immutable source bytes plus Node extracted metadata, exact canonical UTF-8 payload, SHA, pHash; JPEG EXIF/date/GPS/orientation, PNG text/XMP/alpha, WebP lossless/lossy/alpha, metadata absent/malformed, Unicode and number/date serialization. Save recompressed/resized/brighter variants as bytes; Java re-encoding will not reproduce Node fixture bytes by assumption. Existing PNG fixture yields JPEG and resized distance2, brightness distance0; these are **exact asserted distances**, not merely within a threshold. Recompare uploaded fixtures to unchanged existing SQLite hashes. If bit-identical compatibility cannot be achieved, migration is blocked until a separately reviewed compatibility/versioned-hash strategy exists; never overwrite old fingerprints to make tests pass.

## 7. SQLite schema and migration contract

Use JDBC against a **disposable consistent copy** of the selected DB and copied storage in the implementation phase. Do not initialize the original. SQLite foreign_keys must be enabled on every connection, before transactions. Preserve integer ids, sqlite_sequence progression, NULLs, case-sensitive stored text, REAL balances, JSON strings and textual timestamps. Node uses one connection plus an in-process promise queue for auth creation, Vault batch adds, wallet mutation, OCR save and marketplace purchase; Java concurrency requires equivalent transaction boundaries and isolation, not merely synchronized service methods on unrelated connections. No explicit WAL/busy-timeout configuration exists in reference code.

The following is the complete audited base DDL from `server/src/database/schema.sql`; it is included to freeze every column, default, index, uniqueness/check constraint and foreign-key action, including tables without active APIs. AUTOINCREMENT's internal sqlite_sequence and implicit SQLite UNIQUE/PK autoindexes must remain intact. No triggers/views are defined by application schema (tests create one temporary failure trigger).

<!-- BEGIN AUDITED BASE SCHEMA -->

```sql
-- users table
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  full_name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'USER' CHECK(role IN ('SUPER_ADMIN', 'MODERATOR', 'FINANCE_ADMIN', 'VERIFICATION_ADMIN', 'USER')),
  status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'suspended', 'review')),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- wallets table
CREATE TABLE IF NOT EXISTS wallets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL UNIQUE,
  balance REAL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- wallet_transactions table
CREATE TABLE IF NOT EXISTS wallet_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  wallet_id INTEGER NOT NULL,
  type TEXT NOT NULL,
  amount REAL NOT NULL,
  description TEXT,
  reference_id TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(wallet_id) REFERENCES wallets(id) ON DELETE CASCADE
);

-- assets table
CREATE TABLE IF NOT EXISTS assets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  category TEXT,
  file_name TEXT NOT NULL,
  file_path TEXT NOT NULL,
  file_size INTEGER,
  mime_type TEXT,
  status TEXT DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
);

-- asset_metadata table
CREATE TABLE IF NOT EXISTS asset_metadata (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  asset_id INTEGER NOT NULL UNIQUE,
  width INTEGER,
  height INTEGER,
  camera TEXT,
  location TEXT,
  created_date DATETIME,
  metadata_json TEXT,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE
);

-- asset_hashes table
CREATE TABLE IF NOT EXISTS asset_hashes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  asset_id INTEGER NOT NULL UNIQUE,
  sha256_hash TEXT,
  phash TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE
);

-- documents table
CREATE TABLE IF NOT EXISTS documents (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id INTEGER NOT NULL,
  asset_id INTEGER,
  original_name TEXT NOT NULL,
  stored_name TEXT NOT NULL,
  file_path TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  sha256_hash TEXT NOT NULL,
  page_count INTEGER,
  language TEXT DEFAULT 'eng',
  ocr_status TEXT NOT NULL DEFAULT 'pending',
  ocr_error TEXT,
  ocr_processed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE SET NULL
);

-- ocr_results table
CREATE TABLE IF NOT EXISTS ocr_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  document_id INTEGER NOT NULL UNIQUE,
  extracted_text TEXT,
  confidence REAL,
  semantic_hash TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE
);

-- verification_reports table
CREATE TABLE IF NOT EXISTS verification_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER,
  asset_id INTEGER,
  verification_type TEXT,
  sha256_match INTEGER,
  similarity_score REAL,
  status TEXT,
  report_json TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE SET NULL
);

-- blockchain_blocks table
CREATE TABLE IF NOT EXISTS blockchain_blocks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  block_index INTEGER NOT NULL UNIQUE,
  asset_id INTEGER,
  owner_id INTEGER,
  action TEXT NOT NULL,
  previous_hash TEXT,
  current_hash TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
);

-- marketplace_listings table
CREATE TABLE IF NOT EXISTS marketplace_listings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  public_reference TEXT UNIQUE,
  asset_id INTEGER NOT NULL,
  seller_id INTEGER NOT NULL,
  buyer_id INTEGER,
  title TEXT,
  description TEXT,
  listing_type TEXT,
  price REAL,
  status TEXT DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  sold_at DATETIME,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(seller_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(buyer_id) REFERENCES users(id) ON DELETE SET NULL
);

-- settled marketplace sales and VaultChain commission ledger
CREATE TABLE IF NOT EXISTS marketplace_transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL UNIQUE,
  asset_id INTEGER NOT NULL,
  listing_id INTEGER,
  seller_id INTEGER NOT NULL,
  buyer_id INTEGER NOT NULL,
  sale_amount REAL NOT NULL,
  platform_fee REAL NOT NULL DEFAULT 0,
  seller_amount REAL NOT NULL,
  status TEXT NOT NULL DEFAULT 'completed' CHECK(status IN ('pending', 'completed', 'refunded', 'failed')),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE RESTRICT,
  FOREIGN KEY(listing_id) REFERENCES marketplace_listings(id) ON DELETE SET NULL,
  FOREIGN KEY(seller_id) REFERENCES users(id) ON DELETE RESTRICT,
  FOREIGN KEY(buyer_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS admin_activity_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  admin_id INTEGER NOT NULL,
  action TEXT NOT NULL,
  target_type TEXT,
  target_id TEXT,
  details_json TEXT,
  ip_address TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(admin_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS platform_settings (
  setting_key TEXT PRIMARY KEY,
  setting_value TEXT NOT NULL,
  updated_by INTEGER,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(updated_by) REFERENCES users(id) ON DELETE SET NULL
);

-- ownership_history table
CREATE TABLE IF NOT EXISTS ownership_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  asset_id INTEGER NOT NULL,
  previous_owner INTEGER,
  new_owner INTEGER,
  listing_id INTEGER,
  price REAL,
  transaction_reference TEXT UNIQUE,
  transfer_type TEXT,
  blockchain_block_id INTEGER,
  transferred_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(previous_owner) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY(new_owner) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY(listing_id) REFERENCES marketplace_listings(id) ON DELETE SET NULL,
  FOREIGN KEY(blockchain_block_id) REFERENCES blockchain_blocks(id) ON DELETE SET NULL
);

-- fractional_ownership table
CREATE TABLE IF NOT EXISTS fractional_ownership (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  asset_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  percentage REAL,
  shares INTEGER,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE(asset_id, user_id)
);

-- vault_items table
CREATE TABLE IF NOT EXISTS vault_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  encrypted_path TEXT NOT NULL,
  encryption_algorithm TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
);

-- organizational vault collections
CREATE TABLE IF NOT EXISTS vaults (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  public_reference TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT,
  password_hash TEXT,
  auto_lock_minutes INTEGER NOT NULL DEFAULT 10 CHECK(auto_lock_minutes IN (5, 10, 30)),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vault_assets (
  vault_id INTEGER NOT NULL,
  asset_id INTEGER NOT NULL,
  added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(vault_id, asset_id),
  FOREIGN KEY(vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vault_unlock_sessions (
  vault_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  token_fingerprint TEXT NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(vault_id, token_fingerprint),
  FOREIGN KEY(vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vault_unlock_attempts (
  vault_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  window_started_at DATETIME NOT NULL,
  blocked_until DATETIME,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(vault_id, user_id),
  FOREIGN KEY(vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- notifications table
CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  message TEXT NOT NULL,
  is_read INTEGER DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_assets_owner_id ON assets(owner_id);
CREATE INDEX IF NOT EXISTS idx_assets_status ON assets(status);
CREATE INDEX IF NOT EXISTS idx_assets_created_at ON assets(created_at);
CREATE INDEX IF NOT EXISTS idx_asset_hashes_sha256_hash ON asset_hashes(sha256_hash);
CREATE INDEX IF NOT EXISTS idx_asset_hashes_phash ON asset_hashes(phash);
CREATE INDEX IF NOT EXISTS idx_verification_reports_asset_id ON verification_reports(asset_id);
CREATE INDEX IF NOT EXISTS idx_verification_reports_created_at ON verification_reports(created_at);
CREATE INDEX IF NOT EXISTS idx_blockchain_blocks_asset_id ON blockchain_blocks(asset_id);
CREATE INDEX IF NOT EXISTS idx_blockchain_blocks_block_index ON blockchain_blocks(block_index);
CREATE INDEX IF NOT EXISTS idx_marketplace_listings_status ON marketplace_listings(status);
CREATE INDEX IF NOT EXISTS idx_marketplace_listings_created_at ON marketplace_listings(created_at);
CREATE INDEX IF NOT EXISTS idx_marketplace_transactions_created_at ON marketplace_transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_marketplace_transactions_status ON marketplace_transactions(status);
CREATE INDEX IF NOT EXISTS idx_marketplace_transactions_status_created_at ON marketplace_transactions(status, created_at);
CREATE INDEX IF NOT EXISTS idx_marketplace_transactions_seller ON marketplace_transactions(seller_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_transactions_buyer ON marketplace_transactions(buyer_id);
CREATE INDEX IF NOT EXISTS idx_admin_activity_logs_admin ON admin_activity_logs(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_activity_logs_created_at ON admin_activity_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_ownership_history_asset_id ON ownership_history(asset_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_documents_owner_id ON documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_vaults_user_id ON vaults(user_id);
CREATE INDEX IF NOT EXISTS idx_vault_assets_asset_id ON vault_assets(asset_id);
CREATE INDEX IF NOT EXISTS idx_vault_unlock_sessions_user_token ON vault_unlock_sessions(user_id, token_fingerprint);
CREATE INDEX IF NOT EXISTS idx_vault_unlock_sessions_expires_at ON vault_unlock_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_vault_unlock_attempts_blocked_until ON vault_unlock_attempts(blocked_until);
```

<!-- END AUDITED BASE SCHEMA -->

Initialization runs base DDL, then the following **in order**, once per process via a cached promise (including a cached rejected promise on failure). This is not a global migration transaction.

| Step | Existing database behavior / added constraints |
| --- | --- |
| Verification ownership | Inspect table_info. Add nullable `user_id INTEGER REFERENCES users(id) ON DELETE CASCADE` if absent; fill null user_id from current asset.owner_id. If asset_id is NOT NULL, disable FKs outside transaction, rebuild table with nullable asset_id and current user/asset FKs, copy all ids/fields/timestamps, drop/rename, commit, reenable FKs. Create `idx_verification_reports_user_id ON verification_reports(user_id)`. |
| Vault passwords | Add nullable password_hash TEXT if absent; add auto_lock_minutes INTEGER NOT NULL DEFAULT10 CHECK IN(5,10,30) if absent. Existing unprotected Vaults stay passwordless. |
| Marketplace ownership | Add missing public_reference TEXT, buyer_id INTEGER REFERENCES users(id) ON DELETE SET NULL, title TEXT, description TEXT, sold_at DATETIME. Backfill null references `ML-` + `printf('%06X',id)` (hex, minimum width6); null title from asset title or `Marketplace asset`. Convert removed->cancelled. For duplicate active asset listings keep MAX(id), cancel other active rows. Create unique `idx_marketplace_public_reference(public_reference)` and partial unique `idx_marketplace_active_asset(asset_id) WHERE status='active'`. |
| Ownership history | Add listing_id INTEGER REFERENCES marketplace_listings(id) ON DELETE SET NULL, price REAL, transaction_reference TEXT if absent. Null reference -> `TX-` + `printf('%06X',id)`; unique `idx_ownership_transaction_reference(transaction_reference)`. |
| Documents | Add missing original_name/stored_name/file_path/mime_type/sha256_hash TEXT, file_size INTEGER, ocr_status TEXT NOT NULL DEFAULT pending, ocr_error TEXT, ocr_processed_at DATETIME. Legacy added file columns are nullable and **not backfilled**; do not assume fresh-schema NOT NULL constraints apply to old rows. Create owner index if absent. |
| Admin platform | Add missing user status TEXT NOT NULL DEFAULT active (legacy addition has no CHECK). Uppercase all nonnull roles; map null/unknown roles to USER. Insert-or-ignore marketplace_commission_rate='0.05', minimum_listing_price='1', preserving existing settings. Insert-or-ignore marketplace transaction ledger from ownership_history marketplace_sale rows with reference, nonnull owners/price; use **current** commission setting, SQLite ROUND(price*rate,2) and ROUND(price-price*rate,2), completed status and transferred_at. No wallet backfill/adjustment. |

Migration edge cases that the Java port must characterize:

- Verification rebuild drops base indexes on asset_id/created_at that were created before the rebuild. Only user_id index is explicitly recreated in that run; a later process initialization reapplies base indexes. Do not claim every legacy DB has all fresh-schema indexes immediately.
- Base DDL is `CREATE IF NOT EXISTS`, so existing table constraints are not generally upgraded. Role normalization may conflict with a historical CHECK constraint; no old-user-table rebuild is implemented. Partial migrations can remain after errors.
- Historical report ownership backfill uses asset's current owner at migration time, not an original requester that no longer exists in the schema. Null asset -> null user_id, which keeps the report out of user history. Preserve report bytes/ids when rebuilding.
- Derived ML/TX backfill references exceeding six hex digits are legal DB text but do not pass current ML route regex. Existing duplicate nonnull public references are not deduplicated and can make unique-index creation fail.
- Historical ledger backfill independently rounds fee and seller amount, which may differ at floating-point ties from live JS fee-first rounding. INSERT OR IGNORE avoids duplicate references; it does not retroactively know the historical commission. Settings changes never recompute existing transactions.
- Legacy document rows lacking a stored filename may list but fail content/OCR. Record these rows on a disposable copy before deciding any remediation; do not invent file names or recompute old data silently.

`server/scripts/promoteAdmin.js` is the only admin bootstrap CLI: normalized email plus optional admin role (default SUPER_ADMIN), initialize DB, update existing user's role/status active/updated_at, reject missing account. It creates no new admin and does not emit an admin audit row. Preserve a deployment-compatible alternative in a later phase; there is no admin-create endpoint.

## 8. Frontend service contract audit

All nine files were inspected; the server endpoint inventory includes all their requests. Most service methods unwrap a named property; preserving just a generic success flag is insufficient.

| Service file | Caller behavior that must keep working |
| --- | --- |
| `client/src/services/authService.js` | JSON register/login; stores token under `vaultchain_token` in localStorage, returns user. /me maps either camelCase or snake_case; profile unwraps user; password returns envelope. Logout POST best-effort then clears local token. getCurrentUserId reads JWT id. |
| `client/src/services/assetService.js` | GET list/detail/metadata/hashes/history unwrap respective field. Upload FormData title/category/description/file returns entire envelope; ownership check unwraps result. Asset request errors attach HTTP status. Content fetch Bearer -> blob -> object URL, handles JSON or empty errors. |
| `client/src/services/vaultService.js` | Encodes references; mutations JSON; list returns `{vaults,stats}`, others unwrap vault. Delete expects204 and does not parse JSON; remove membership expects vault JSON. |
| `client/src/services/verificationService.js` | FormData file only; create/list/detail unwrap verification/verifications; URI-encodes report reference. |
| `client/src/services/documentService.js` | Truthy filter values encoded as query; upload file only; list/detail/OCR/retry unwrap documents/document/ocr/document. Delete expects204; content uses authenticated blob/object URL. |
| `client/src/services/walletService.js` | Unwrap wallet/list transactions; manual mutation sends only type/amount/description and returns full envelope (backend additionally accepts referenceId). |
| `client/src/services/marketplaceService.js` | Create assetId/title/description/price JSON; PATCH arbitrary payload; list/detail/create/update/delete unwrap listings/listing; delete expects200 JSON, purchase unwraps receipt. Authenticated blob preview. |
| `client/src/services/dashboardService.js` | GET summary with Bearer, unwrap summary. |
| `client/src/services/adminService.js` | Always JSON content header+Bearer; unwrap endpoint-specific key; encodes truthy range/from/to. Notifications read204 ->null; settings mutation returns envelope. Never requires success field. |

Every service reads error.message as user-facing text. No client rewrite is required to migrate; Java must keep absolute-base URL/CORS support and the same response bodies. Changes to refresh tokens, cookie auth, response wrapping, casing, pagination, OCR202 jobs, or marketplace DELETE204 are contract changes, not implementation details.

## 9. High-risk parity decisions and acceptance gates

| Risk | Evidence / required proof before cutover |
| --- | --- |
| Asset SHA is metadata-dependent | Golden file+exifr metadata+canonical payload+SHA vectors must match Java byte-for-byte; do not replace with raw file SHA. |
| image-hash implementation and decode variation | Missing installed algorithm source; inspect pinned5.3.2, freeze256-bit vectors, JPEG/PNG/WebP/alpha/EXIF orientation and exact variant distances. Threshold changes cannot compensate for incompatible hashes. |
| Existing database/storage selection | Confirm actual DATABASE_PATH and three storage roots; consistent copy, checksums/counts, all constraints/migrations, nullable legacy rows, preserved ids/references/hashes. Do not select sqlite.db merely because it exists. |
| Tokens and BCrypt | Old Node token -> Java verification and DB authorization refresh; Java token -> Node; raw secret handling, jti, expiry parsing, multibyte/truncation/hash prefixes. Preserve narrow logout behavior. |
| Vault session/attempt timing | Independent JWTs, shared attempts, strict expiry, fixed TTL, every-Vault conjunction, password-reset all-session revocation. SQL admin security uses textual `expires_at > CURRENT_TIMESTAMP` against ISO timestamps, which can overcount same-day expired sessions; contract discrepancy must be explicitly resolved. |
| Privacy inconsistencies | Vault embedded DTO ignores other Vaults; own ownership check reveals title under lock; legacy registeredAsset spread can reintroduce stored fields; historical match owner labels remain stale; purchase exposes sellerBalance; overview shares transaction names with non-finance admin roles. Capture and decide intentional changes separately. |
| Sale transaction concurrency/rounding | SQLite REAL/JS Math.round/SQLite ROUND versus BigDecimal; inject failures after every write, race buyers, one ledger group, unchanged file/hash/metadata/report; handle post-commit response errors separately. |
| OCR output and availability | Node bundled English Tesseract and Poppler vs Java OCR/PDF engine: fixed image/PDF bytes, text/whitespace/page order/confidence; no claim that Tess4J/PDFBox defaults reproduce output. Digital PDF10-char shortcut, scanned10-page cap and failed201/200 semantics must remain. |
| JSON/validation/error transport | Mixed snake/camel names, omitted vs null, array order, numeric coercion/UTF-16 length, auth-before-multipart, status codes and literal messages. Security filter/validation exceptions must use E(message). |
| Schema migration and runtime gaps | No full integration execution in audit; preserve non-versioned incremental behavior, backfills and repeat-start behavior. No replacement runtime scripts until all checklist gates pass. |

Proposed first implementation architecture (not created in this task): Java21, Spring Boot/Maven, Web/Security/Validation/JDBC/Jackson/SQLite JDBC, HMAC JWT + BCrypt, compatible metadata/image pipeline, Java OCR/PDF integration, JUnit5 + Spring Boot Test. This is the requested target, not an assurance that any default library behavior is compatible.

Phase gates: (1) this contract/checklist; (2) isolated spring-server scaffold and disposable-copy initialization; (3) auth/health; (4) assets/storage/fingerprints/checks/content; (5) Vaults; (6) verification/history; (7) documents/OCR; (8) wallet/marketplace/history; (9) dashboard/admin; (10) differential contract tests and then runtime switch. Keep Node executable throughout. Differential tests need identical cloned initial data/config/files and normalized nondeterministic fields only (random reference correspondence, tokens/jti, timestamps, uptime/latency); do not normalize away money, hashes, privacy fields, missing/null, messages or status codes.
