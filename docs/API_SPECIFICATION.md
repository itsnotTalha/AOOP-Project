# AuthVault API Specification and Team Contract

**Version:** 1.0 draft  
**Base URL:** `http://localhost:8080/api/v1` for new modules  
**Authentication:** `Authorization: Bearer <JWT>` unless an endpoint is marked Public  
**Content type:** `application/json`, except upload endpoints use `multipart/form-data`

This document is the shared contract for implementing Digital Asset Authentication, Document Verification, Secure Vault, Marketplace, Fractional Ownership, and their dashboard integrations. It defines what each API must do before teammates build controllers or frontend pages.

## 1. Current API Compatibility

The repository already exposes these unversioned endpoints. They remain supported while new modules use `/api/v1`.

| Method | Existing endpoint | Status |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Implemented |
| `POST` | `/api/auth/login` | Implemented |
| `GET` | `/api/users/me` | Implemented |
| `PUT` | `/api/users/me` | Implemented |
| `PUT` | `/api/users/me/password` | Implemented |
| `GET` | `/api/dashboard/summary` | Implemented |

New work should not change these routes without coordinating a frontend migration. A later cleanup may expose them under `/api/v1` as well.

## 2. Shared API Rules

### 2.1 Resource identifiers

- Use public UUID strings in URLs and API responses.
- Never expose database primary keys.
- Name URL parameters `{assetId}`, `{reportId}`, `{vaultItemId}`, and `{listingId}`.
- Generate identifiers on the server.

### 2.2 Standard success envelope

```json
{
  "success": true,
  "message": "Asset uploaded successfully",
  "data": {},
  "timestamp": "2026-08-06T10:30:00Z",
  "requestId": "req_01J4..."
}
```

For paginated data, `data` uses this structure:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "hasNext": false
}
```

### 2.3 Standard error envelope

```json
{
  "success": false,
  "code": "ASSET_NOT_FOUND",
  "message": "The requested asset does not exist",
  "errors": [],
  "timestamp": "2026-08-06T10:30:00Z",
  "requestId": "req_01J4..."
}
```

### 2.4 HTTP status usage

| Status | Use |
| --- | --- |
| `200 OK` | Successful read or update |
| `201 Created` | Resource created |
| `202 Accepted` | Long-running OCR or verification accepted |
| `204 No Content` | Successful deletion with no response body |
| `400 Bad Request` | Invalid input or invalid state transition |
| `401 Unauthorized` | Missing, invalid, or expired JWT |
| `403 Forbidden` | Authenticated user does not own or cannot access resource |
| `404 Not Found` | Resource does not exist or is hidden from requester |
| `409 Conflict` | Duplicate, concurrent update, or unavailable listing |
| `413 Payload Too Large` | File exceeds configured limit |
| `415 Unsupported Media Type` | Unsupported file format |
| `422 Unprocessable Entity` | File is readable but cannot be analyzed |
| `429 Too Many Requests` | Rate limit exceeded |

### 2.5 Pagination and filters

List endpoints accept:

- `page=0`
- `size=20`, maximum `100`
- `sort=createdAt,desc`
- Feature-specific filter parameters documented below

### 2.6 Authorization rules

- Users can read and modify only their private assets, reports, vault items, wallet, and notifications.
- Public marketplace listings are readable by authenticated users.
- A seller must own the asset or sufficient fractional shares before listing it.
- The server derives the current user from the JWT. Client requests must never send an authoritative `ownerId`, `sellerId`, or `buyerId`.
- Admin-only operations must use method-level authorization such as `@PreAuthorize("hasRole('ADMIN')")`.

### 2.7 Idempotency and concurrency

- Purchase, transfer, upload finalization, and share operations accept an `Idempotency-Key` header.
- Marketplace purchase and ownership mutations must run inside database transactions.
- Listings and wallets should have optimistic-lock version fields to prevent double purchase or double spending.

## 3. Core Enums

### Asset type

`IMAGE`, `DOCUMENT`

### Authentication classification

| Value | Meaning |
| --- | --- |
| `ORIGINAL` | No exact or similar prior asset was found and integrity checks passed |
| `MODIFIED_COPY` | Visually similar content exists, but bytes or relevant metadata differ |
| `DUPLICATE` | Exact SHA-256 match already exists |
| `UNKNOWN` | Analysis failed, confidence is insufficient, or format is unsupported |

### Processing status

`UPLOADED`, `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`

### Verification result

`VERIFIED`, `TAMPERED`, `INCONCLUSIVE`, `FAILED`

### Listing status

`DRAFT`, `ACTIVE`, `SOLD`, `CANCELLED`, `EXPIRED`

### Listing type

`FULL_ASSET`, `FRACTIONAL_SHARES`, `AUCTION`

### Vault item type

`FILE`, `SECRET_NOTE`

## 4. Digital Asset Authentication APIs

Suggested teammate ownership: **Asset Authentication Team**.

The step-by-step backend and frontend implementation guide for this module is available in [IMAGE_AUTHENTICATION_IMPLEMENTATION_GUIDE.md](IMAGE_AUTHENTICATION_IMPLEMENTATION_GUIDE.md).

### Endpoint list

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/assets/images` | Upload image and start SHA-256, pHash, metadata, duplicate, and similarity analysis |
| `GET` | `/api/v1/assets` | List current user's assets |
| `GET` | `/api/v1/assets/{assetId}` | Read asset details and latest authentication result |
| `GET` | `/api/v1/assets/{assetId}/status` | Poll asynchronous analysis status |
| `GET` | `/api/v1/assets/{assetId}/metadata` | Read normalized extracted metadata |
| `GET` | `/api/v1/assets/{assetId}/similarities` | Read exact and similar matches visible to the user |
| `POST` | `/api/v1/assets/{assetId}/reanalyze` | Re-run authentication with the current algorithm version |
| `GET` | `/api/v1/assets/{assetId}/download` | Securely download the original file |
| `DELETE` | `/api/v1/assets/{assetId}` | Soft-delete an owned asset when it is not listed or transferred |

### Upload image

`POST /api/v1/assets/images`

Request: `multipart/form-data`

| Part | Type | Required | Rules |
| --- | --- | --- | --- |
| `file` | binary | Yes | JPEG, PNG, or WebP; recommended maximum 25 MB |
| `title` | string | Yes | 1–150 characters |
| `description` | string | No | Maximum 2,000 characters |

Processing requirements:

1. Validate MIME type using file content, not only the filename.
2. Stream the file while calculating SHA-256.
3. Check exact duplicates using the indexed SHA-256 column.
4. Extract width, height, color space, EXIF, capture timestamp, device, GPS if present, and normalized MIME information.
5. Remove sensitive metadata from public responses; GPS should be private by default.
6. Generate a perceptual hash from normalized image pixels.
7. Compare pHash values using Hamming distance.
8. Store the original file and immutable analysis record.
9. Return `DUPLICATE`, `MODIFIED_COPY`, `ORIGINAL`, or `UNKNOWN` with evidence and confidence.

Recommended default classification rules:

- Exact SHA-256 match: `DUPLICATE`, confidence `1.0`.
- No SHA match and pHash distance at or below `8`: `MODIFIED_COPY`.
- No SHA match and no pHash candidate inside the configured threshold: `ORIGINAL`.
- Decode, hash, or extraction failure: `UNKNOWN`.
- Keep the threshold configurable and version the algorithm; do not hard-code it in controllers.

Response: `201 Created` for synchronous completion or `202 Accepted` when queued.

```json
{
  "success": true,
  "message": "Image accepted for authentication",
  "data": {
    "assetId": "ast_55db7f8e",
    "title": "Artwork proof",
    "assetType": "IMAGE",
    "originalFilename": "artwork.png",
    "mimeType": "image/png",
    "fileSize": 482011,
    "processingStatus": "COMPLETED",
    "classification": "ORIGINAL",
    "confidence": 0.98,
    "sha256": "4c4b6a3b...64-hex-characters",
    "perceptualHash": "a17fd2239910bc4e",
    "metadata": {
      "width": 1920,
      "height": 1080,
      "colorSpace": "sRGB",
      "capturedAt": null,
      "cameraMake": null
    },
    "exactMatch": null,
    "similarMatches": [],
    "uploadedAt": "2026-08-06T10:30:00Z"
  }
}
```

### List assets

`GET /api/v1/assets?type=IMAGE&classification=ORIGINAL&status=COMPLETED&query=art&page=0&size=20&sort=uploadedAt,desc`

Each list item includes `assetId`, `title`, `assetType`, thumbnail URL, classification, processing status, verification status, file size, current owner summary, and timestamps.

### Similarity response

`GET /api/v1/assets/{assetId}/similarities`

```json
{
  "success": true,
  "data": {
    "assetId": "ast_55db7f8e",
    "classification": "MODIFIED_COPY",
    "exactMatch": null,
    "matches": [
      {
        "matchedAssetId": "ast_2dc9e802",
        "hammingDistance": 5,
        "similarityScore": 0.9219,
        "relationship": "POSSIBLE_MODIFIED_COPY",
        "visibleOwner": false
      }
    ],
    "algorithm": "PHASH_DCT",
    "algorithmVersion": "1.0"
  }
}
```

Privacy rule: similarity detection may report that a match exists without exposing another user's private file, filename, metadata, or identity.

## 5. Document Verification APIs

Suggested teammate ownership: **Document Verification Team**.

### Endpoint list

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/documents/verifications` | Upload PDF/image and start OCR and tamper analysis |
| `POST` | `/api/v1/documents/compare` | Compare a candidate document with an owned reference document |
| `GET` | `/api/v1/documents/verifications` | List user's verification reports |
| `GET` | `/api/v1/documents/verifications/{reportId}` | Read a complete verification report |
| `GET` | `/api/v1/documents/verifications/{reportId}/status` | Poll OCR/analysis status |
| `GET` | `/api/v1/documents/verifications/{reportId}/text` | Read extracted OCR text when authorized |
| `GET` | `/api/v1/documents/verifications/{reportId}/download` | Download verification report as PDF or JSON |
| `POST` | `/api/v1/documents/verifications/{reportId}/reanalyze` | Repeat analysis with current algorithms |
| `DELETE` | `/api/v1/documents/verifications/{reportId}` | Soft-delete report and governed source file |

### Upload and verify document

`POST /api/v1/documents/verifications`

Request: `multipart/form-data`

| Part | Type | Required | Rules |
| --- | --- | --- | --- |
| `file` | binary | Yes | PDF, JPEG, PNG, or TIFF; recommended maximum 50 MB |
| `title` | string | Yes | 1–150 characters |
| `referenceAssetId` | string | No | Owned reference used for comparison |
| `languageHint` | string | No | ISO language code such as `en` |

Processing pipeline:

1. Validate and virus-scan the file.
2. Calculate file SHA-256 and extract native PDF text when available.
3. Render PDF pages safely and run OCR on pages without trustworthy text.
4. Normalize Unicode, whitespace, punctuation, dates, and case for comparison while preserving original extracted text.
5. Create a versioned semantic fingerprint; do not call a simple cryptographic digest “semantic.”
6. Compare page structure, text blocks, embedded images, fonts, PDF signatures, and metadata where available.
7. Produce field-level and page-level differences.
8. Calculate an explainable tamper score and result.
9. Store an immutable verification report.

Response: `202 Accepted`

```json
{
  "success": true,
  "message": "Document verification started",
  "data": {
    "reportId": "vrf_c1bc07e0",
    "assetId": "ast_12fe8891",
    "processingStatus": "QUEUED",
    "statusUrl": "/api/v1/documents/verifications/vrf_c1bc07e0/status"
  }
}
```

### Compare existing documents

`POST /api/v1/documents/compare`

```json
{
  "referenceAssetId": "ast_original",
  "candidateAssetId": "ast_candidate",
  "options": {
    "ignoreWhitespace": true,
    "ignoreCase": true,
    "compareImages": true,
    "compareMetadata": true
  }
}
```

### Verification report

`GET /api/v1/documents/verifications/{reportId}`

```json
{
  "success": true,
  "data": {
    "reportId": "vrf_c1bc07e0",
    "assetId": "ast_12fe8891",
    "referenceAssetId": "ast_original",
    "processingStatus": "COMPLETED",
    "result": "TAMPERED",
    "confidence": 0.94,
    "tamperScore": 0.81,
    "textSimilarity": 0.87,
    "semanticSimilarity": 0.91,
    "visualSimilarity": 0.78,
    "sha256": "17aba0...",
    "semanticHash": "sem_v1_...",
    "ocr": {
      "engine": "TESSERACT",
      "engineVersion": "5.x",
      "language": "en",
      "pageCount": 3,
      "averageConfidence": 0.93
    },
    "findings": [
      {
        "severity": "HIGH",
        "type": "TEXT_CHANGED",
        "page": 2,
        "location": { "x": 0.12, "y": 0.44, "width": 0.40, "height": 0.06 },
        "expected": "Total: $1,000.00",
        "observed": "Total: $9,000.00",
        "explanation": "A material field differs from the reference document"
      }
    ],
    "algorithmVersion": "docverify-1.0",
    "verifiedAt": "2026-08-06T10:35:00Z"
  }
}
```

The report must separate detected evidence from the final classification. OCR confidence alone must not be treated as proof of authenticity.

## 6. Secure Vault APIs

Suggested teammate ownership: **Secure Vault Team**.

Use AES-256-GCM, which provides authenticated encryption. Do not use ECB mode. Each encrypted item requires a unique random 96-bit IV/nonce. Derive a key-encryption key from the vault password using Argon2id or PBKDF2 with a unique salt; never store the vault password or raw encryption key.

### Endpoint list

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/vaults` | Create and password-protect a user's vault |
| `GET` | `/api/v1/vaults/me` | Read vault metadata and usage, never secrets |
| `POST` | `/api/v1/vaults/unlock` | Verify vault password and issue short-lived vault access token |
| `POST` | `/api/v1/vaults/lock` | Revoke current vault access token |
| `PUT` | `/api/v1/vaults/password` | Change vault password and re-wrap encryption keys |
| `POST` | `/api/v1/vaults/items/files` | Encrypt and store a file |
| `POST` | `/api/v1/vaults/items/notes` | Encrypt and store a secret note |
| `GET` | `/api/v1/vaults/items` | List safe item metadata |
| `GET` | `/api/v1/vaults/items/{vaultItemId}` | Read safe item metadata |
| `GET` | `/api/v1/vaults/items/{vaultItemId}/download` | Authorize, decrypt, and stream a file |
| `GET` | `/api/v1/vaults/items/{vaultItemId}/note` | Authorize and decrypt a secret note |
| `PUT` | `/api/v1/vaults/items/{vaultItemId}/note` | Replace encrypted note content |
| `DELETE` | `/api/v1/vaults/items/{vaultItemId}` | Permanently delete item after password confirmation |

### Create vault

`POST /api/v1/vaults`

```json
{
  "name": "My private vault",
  "password": "a separate strong vault password"
}
```

Response must contain safe configuration metadata only:

```json
{
  "success": true,
  "data": {
    "vaultId": "vlt_71fecc22",
    "name": "My private vault",
    "status": "LOCKED",
    "encryption": "AES-256-GCM",
    "createdAt": "2026-08-06T11:00:00Z"
  }
}
```

### Unlock vault

`POST /api/v1/vaults/unlock`

```json
{
  "password": "a separate strong vault password"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "vaultAccessToken": "short-lived-random-token",
    "expiresIn": 900
  }
}
```

Send this token on sensitive vault operations:

```text
X-Vault-Token: short-lived-random-token
```

Store only a one-way hash of the access token and expire it after a recommended 15 minutes of inactivity. Rate-limit failed unlock attempts.

### Upload encrypted file

`POST /api/v1/vaults/items/files` using `multipart/form-data`

Parts: `file`, `title`, optional `description`, optional `storageProvider` with `LOCAL` or `IPFS_SIMULATED`.

The service encrypts before writing persistent bytes. For IPFS simulation, generate a deterministic CID-like reference from encrypted bytes and store the encrypted payload locally. Never claim this is connected to the public IPFS network.

### Create secret note

`POST /api/v1/vaults/items/notes`

```json
{
  "title": "Recovery phrase hint",
  "content": "Sensitive note content"
}
```

The response must never echo plaintext content. Application logs must never contain vault passwords, plaintext notes, raw keys, decrypted bytes, or vault tokens.

### Secure download requirements

- Require both valid JWT and valid `X-Vault-Token`.
- Confirm item ownership after resolving the authenticated user.
- Use `Content-Disposition: attachment` and a safe filename.
- Add `Cache-Control: no-store` and `Pragma: no-cache`.
- Stream decryption; do not create persistent plaintext temporary files.
- Audit successful and failed download attempts without storing sensitive content.

## 7. Marketplace APIs

Suggested teammate ownership: **Marketplace Team**.

### Endpoint list

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/marketplace/listings` | List a whole asset or a quantity of shares |
| `GET` | `/api/v1/marketplace/listings` | Browse active listings with filters |
| `GET` | `/api/v1/marketplace/listings/{listingId}` | Read listing details |
| `GET` | `/api/v1/marketplace/listings/mine` | List current user's selling history |
| `PUT` | `/api/v1/marketplace/listings/{listingId}` | Change permitted listing details |
| `POST` | `/api/v1/marketplace/listings/{listingId}/cancel` | Cancel an active listing |
| `POST` | `/api/v1/marketplace/listings/{listingId}/purchase` | Atomically buy listed asset or shares |
| `GET` | `/api/v1/marketplace/purchases` | List current user's purchases |
| `POST` | `/api/v1/assets/{assetId}/transfers` | Transfer or gift full ownership to another user |
| `GET` | `/api/v1/assets/{assetId}/ownership-history` | Read ownership chain allowed by visibility policy |

### Create listing

`POST /api/v1/marketplace/listings`

Whole asset:

```json
{
  "assetId": "ast_55db7f8e",
  "listingType": "FULL_ASSET",
  "price": 2500.00,
  "currency": "USD",
  "expiresAt": "2026-09-06T12:00:00Z"
}
```

Fractional shares:

```json
{
  "assetId": "ast_55db7f8e",
  "listingType": "FRACTIONAL_SHARES",
  "shareQuantity": 100,
  "pricePerShare": 25.00,
  "currency": "USD",
  "expiresAt": "2026-09-06T12:00:00Z"
}
```

Validation:

- Asset must be verified and owned by the seller.
- Asset or shares cannot already be locked by another active listing.
- Price must be positive and use `BigDecimal`.
- A share listing cannot exceed the seller's available, unlocked shares.

### Purchase listing

`POST /api/v1/marketplace/listings/{listingId}/purchase`

Headers:

```text
Authorization: Bearer <JWT>
Idempotency-Key: 8ec10438-0c65-4dcc-a733-fdb598895fca
```

Request for shares:

```json
{
  "shareQuantity": 10
}
```

The purchase transaction must atomically:

1. Lock and re-read the active listing.
2. Prevent the seller from buying their own listing.
3. Confirm buyer funds and seller ownership.
4. Debit buyer and credit seller.
5. Create wallet transaction records.
6. Transfer full ownership or share balances.
7. Update or close the listing.
8. Append ownership history and simulated ledger record.
9. Create notifications.
10. Return the previously stored result for a repeated idempotency key.

Response:

```json
{
  "success": true,
  "message": "Purchase completed",
  "data": {
    "purchaseId": "pur_e838ef31",
    "listingId": "lst_44fc8089",
    "assetId": "ast_55db7f8e",
    "listingType": "FRACTIONAL_SHARES",
    "shareQuantity": 10,
    "total": 250.00,
    "currency": "USD",
    "buyerTransactionId": "txn_9213ab",
    "purchasedAt": "2026-08-06T12:00:00Z"
  }
}
```

### Direct ownership transfer

`POST /api/v1/assets/{assetId}/transfers`

```json
{
  "recipientUsername": "newowner",
  "transferType": "GIFT",
  "note": "Ownership transferred by agreement"
}
```

Require recent password confirmation or a short-lived sensitive-action token before irreversible transfers.

## 8. Fractional Ownership APIs

Suggested teammate ownership: **Fractional Ownership Team**, coordinated closely with Marketplace.

### Endpoint list

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/assets/{assetId}/fractions` | Split a fully owned asset into a fixed number of shares |
| `GET` | `/api/v1/assets/{assetId}/fractions` | Read capitalization/share summary |
| `GET` | `/api/v1/assets/{assetId}/shareholders` | Read shareholders according to privacy policy |
| `GET` | `/api/v1/portfolio/fractions` | List current user's fractional holdings |
| `POST` | `/api/v1/assets/{assetId}/fractions/transfers` | Transfer shares directly to another user |
| `POST` | `/api/v1/assets/{assetId}/fractions/consolidate` | Restore full ownership when one user owns all shares |

### Split asset into shares

`POST /api/v1/assets/{assetId}/fractions`

```json
{
  "totalShares": 1000,
  "symbol": "ARTX",
  "minimumTransferShares": 1
}
```

Rules:

- Only the verified full owner may fractionalize the asset.
- The asset must not already be fractionalized or actively listed.
- `totalShares` is immutable after creation in version 1.
- Store integer shares as the source of truth; derive percentages for display.
- The sum of all shareholder balances must always equal `totalShares`.

Response:

```json
{
  "success": true,
  "data": {
    "assetId": "ast_55db7f8e",
    "symbol": "ARTX",
    "totalShares": 1000,
    "availableShares": 1000,
    "ownerShares": 1000,
    "ownerPercentage": 100.0,
    "fractionalizedAt": "2026-08-06T12:30:00Z"
  }
}
```

### Transfer shares

`POST /api/v1/assets/{assetId}/fractions/transfers`

```json
{
  "recipientUsername": "shareholder2",
  "shares": 50,
  "note": "Private transfer"
}
```

Validate available unlocked shares and execute with a database transaction and idempotency key.

## 9. Optional Auction APIs

Auctions are phase two and should not block fixed-price marketplace delivery.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/marketplace/auctions` | Create timed auction |
| `GET` | `/api/v1/marketplace/auctions` | Browse active auctions |
| `GET` | `/api/v1/marketplace/auctions/{auctionId}` | Read auction and bid state |
| `POST` | `/api/v1/marketplace/auctions/{auctionId}/bids` | Place a bid |
| `POST` | `/api/v1/marketplace/auctions/{auctionId}/cancel` | Cancel only when rules allow |
| `POST` | `/api/v1/marketplace/auctions/{auctionId}/settle` | Admin/system settlement operation |

An auction needs bid escrow or wallet reservation. Do not implement bidding without transactional balance reservation and deterministic settlement rules.

## 10. Wallet APIs Required by Marketplace

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/wallet` | Read balance and totals |
| `GET` | `/api/v1/wallet/transactions` | Paginated transaction history |
| `POST` | `/api/v1/wallet/deposits/simulate` | Add demo funds in development only |
| `POST` | `/api/v1/wallet/withdrawals/simulate` | Remove demo funds in development only |

Simulation endpoints must be disabled outside the development profile. Marketplace services, not controllers, own internal debit and credit operations.

## 11. Dashboard and Activity APIs

Suggested teammate ownership: **Dashboard Integration Team** after other module contracts stabilize.

| Method | Endpoint | Dashboard component |
| --- | --- | --- |
| `GET` | `/api/v1/dashboard/summary` | Wallet, total assets, verified assets, storage, notifications |
| `GET` | `/api/v1/dashboard/activity` | Recent uploads, verifications, vault access, sales, and transfers |
| `GET` | `/api/v1/dashboard/security-health` | Vault and security status |
| `GET` | `/api/v1/dashboard/asset-breakdown` | Classification and asset-type chart data |
| `GET` | `/api/v1/notifications` | Notification panel |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Mark one notification read |
| `POST` | `/api/v1/notifications/read-all` | Mark all current user's notifications read |

### Dashboard summary response

```json
{
  "success": true,
  "data": {
    "wallet": {
      "balance": 12480.00,
      "currency": "USD",
      "changePercent": 12.5
    },
    "assets": {
      "total": 248,
      "images": 160,
      "documents": 88,
      "changePercent": 8.2
    },
    "verification": {
      "verified": 196,
      "pending": 12,
      "rejected": 3,
      "rate": 79.03
    },
    "storage": {
      "usedBytes": 75497472,
      "limitBytes": 104857600,
      "percent": 72.0
    },
    "notifications": {
      "unread": 4
    }
  }
}
```

### Activity item contract

```json
{
  "activityId": "act_1a57",
  "type": "ASSET_VERIFIED",
  "title": "Ownership certificate verified",
  "description": "SHA-256 and document checks completed",
  "resourceType": "ASSET",
  "resourceId": "ast_55db7f8e",
  "status": "VERIFIED",
  "amount": null,
  "currency": null,
  "occurredAt": "2026-08-06T10:35:00Z"
}
```

Dashboard queries should use aggregate repository queries or projections. Do not load every entity into memory to calculate totals.

## 12. Supporting Audit and Ledger APIs

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/assets/{assetId}/ledger` | Read simulated immutable history for an authorized asset |
| `GET` | `/api/v1/audit-events` | Read current user's security-relevant events |

The existing `BlockchainLedger` is a simulated append-only application ledger, not a decentralized blockchain. Documentation and UI must describe it accurately.

## 13. Recommended Database Changes

Existing entities are a useful foundation, but the contract requires additional persistence.

### Digital assets

Add or normalize:

- `processing_status`
- `classification`
- `classification_confidence`
- `phash_algorithm` and `algorithm_version`
- `width`, `height`, and safe searchable metadata, while optional raw metadata remains JSON
- `deleted_at` for soft deletion
- Non-unique SHA-256 storage plus a normal index if every user's upload must be recorded; a globally unique SHA column prevents storing a duplicate record

Add an `asset_similarity_matches` table containing source asset, matched asset, Hamming distance, normalized score, relationship, and algorithm version.

### Documents

Add a separate immutable `document_verification_reports` table and `document_findings` table. The current one-to-one `Document` record is not sufficient for repeated verification reports or detailed findings.

### Secure vault

Split vault configuration from vault items:

- `vaults`: owner, name, password KDF salt/configuration, wrapped data key, status, timestamps
- `vault_items`: type, safe title, encrypted data or encrypted storage path, IV, authentication tag if not combined, storage provider, simulated CID, file metadata, timestamps
- `vault_sessions`: hashed token, user, vault, expiry, revoked timestamp
- `vault_audit_events`: action, success, safe request metadata, timestamp

Never persist a password, plaintext encryption key, plaintext secret note, or decrypted file.

### Marketplace and shares

Add:

- Public UUID to listings
- `listing_type`, `share_quantity`, `price_per_share`, and optimistic-lock `version`
- Purchase/order table with idempotency key and immutable price snapshot
- Fraction definition table holding symbol and total shares
- Share balance and share transfer ledger tables
- Wallet balance/version locking or an immutable ledger from which balance can be reconciled

The current `FractionalOwnership.percentage` duplicates information derivable from shares and can drift. Treat integer shares as authoritative.

## 14. Minimum Indexes and Constraints

- Unique `users.uuid`, `users.username`, and `users.email`
- Unique `digital_assets.uuid`; index `sha256_hash`, `owner_id`, `current_owner_id`, status, and upload date
- Index or specialized search structure for perceptual hash candidates
- Unique report UUID and indexes on asset, owner, result, and created date
- Unique vault UUID and compound index on `(user_id, updated_at)`
- Unique listing UUID; indexes on status, type, price, seller, asset, and expiry
- Unique transaction/reference identifiers
- Unique idempotency key scoped to user and operation
- Unique share balance per `(asset_id, owner_id)`
- Check constraints for positive price, positive shares, and non-negative wallet balance

## 15. Security and File-Handling Checklist

- Enforce upload size at HTTP and application layers.
- Detect MIME from content and use an allowlist.
- Rename stored files; never trust client paths or filenames.
- Prevent path traversal and archive expansion attacks.
- Virus-scan uploads when a scanning service is available.
- Keep originals outside the public static directory.
- Apply ownership checks in service methods, not only UI or controllers.
- Never expose hashes as proof of legal ownership; hashes establish content relationships only.
- Strip sensitive EXIF data from public marketplace representations.
- Encrypt vault data with AES-256-GCM and authenticated associated data.
- Use Argon2id or PBKDF2 for password-based key derivation.
- Rate-limit login, vault unlock, OCR, image analysis, purchase, and transfer operations.
- Audit authentication, vault access, purchases, and ownership transfers.
- Return generic `404` instead of revealing private resource existence.
- Use database transactions for every balance or ownership mutation.
- Use environment variables for secrets and production paths.

## 16. Work Allocation and Dependencies

| Team | Owns | Depends on | Deliverables |
| --- | --- | --- | --- |
| Platform/Auth | Common envelopes, UUIDs, security helpers, storage abstraction, migrations | Existing authentication | Shared DTOs, error codes, ownership guard, test utilities |
| Asset Authentication | Section 4 and image analysis persistence | Platform storage | Upload, SHA-256, pHash, metadata, duplicate and similarity APIs |
| Document Verification | Section 5 and report persistence | Platform storage; optionally assets | OCR pipeline, comparisons, findings, report API |
| Secure Vault | Section 6 and vault persistence | Platform security/storage | AES-GCM service, vault sessions, file/note APIs, audit events |
| Marketplace | Section 7, wallet purchase logic, ownership history | Verified assets and wallet | Listings, purchases, transfers, transaction tests |
| Fractional Ownership | Section 8 and share ledger | Assets; marketplace coordination | Fraction definition, balances, share transfer/consolidation |
| Dashboard | Section 11 and frontend integration | All module read models | Aggregates, activity feed, charts, removal of mock dashboard data |
| QA/Documentation | Contract tests and API examples | All teams | Integration tests, Postman/OpenAPI updates, acceptance evidence |

Parallel work recommendation:

1. Platform/Auth defines common contracts and migrations first.
2. Asset, Document, and Vault teams can then work in parallel.
3. Marketplace starts after asset ownership rules and wallet transaction semantics are agreed.
4. Fractional ownership and Marketplace coordinate share locking and purchases.
5. Dashboard integrates only stable response contracts and replaces its presentation data feature by feature.

## 17. Definition of Done for Every Endpoint

An endpoint is not complete until it has:

- Controller, service, repository, request DTO, and response DTO as applicable
- Jakarta validation and explicit business validation
- Authentication and resource-level authorization tests
- Success, validation, forbidden, not-found, conflict, and failure tests
- Transaction boundaries for state mutations
- Standard response and error envelopes
- No entity objects serialized directly
- Database migration or schema documentation
- OpenAPI annotation or specification update
- Example request and response
- Frontend integration or a documented consumer handoff
- No secrets or private data in logs

## 18. Suggested Package Boundaries

```text
com.verivault
├── asset/
│   ├── controller
│   ├── dto
│   ├── service
│   ├── repository
│   └── hashing
├── document/
│   ├── controller
│   ├── dto
│   ├── service
│   ├── ocr
│   └── comparison
├── vault/
│   ├── controller
│   ├── dto
│   ├── service
│   ├── crypto
│   └── storage
├── marketplace/
├── ownership/
├── wallet/
├── dashboard/
└── common/
```

Packages may remain in the repository's current layered structure if the team prefers; what matters is clear ownership and avoiding one large service shared by unrelated features.

## 19. Error Code Registry

| Feature | Required codes |
| --- | --- |
| Asset | `ASSET_NOT_FOUND`, `ASSET_ACCESS_DENIED`, `UNSUPPORTED_IMAGE`, `IMAGE_ANALYSIS_FAILED`, `ASSET_ALREADY_LISTED` |
| Document | `REPORT_NOT_FOUND`, `UNSUPPORTED_DOCUMENT`, `OCR_FAILED`, `REFERENCE_REQUIRED`, `DOCUMENT_COMPARISON_FAILED` |
| Vault | `VAULT_NOT_FOUND`, `VAULT_LOCKED`, `INVALID_VAULT_PASSWORD`, `VAULT_TOKEN_EXPIRED`, `VAULT_ITEM_NOT_FOUND`, `DECRYPTION_FAILED` |
| Marketplace | `LISTING_NOT_FOUND`, `LISTING_NOT_ACTIVE`, `OWN_LISTING_PURCHASE`, `INSUFFICIENT_FUNDS`, `INSUFFICIENT_SHARES`, `ASSET_NOT_TRANSFERABLE` |
| Fractional | `ALREADY_FRACTIONALIZED`, `NOT_FRACTIONALIZED`, `INVALID_SHARE_QUANTITY`, `SHARES_LOCKED`, `SHARE_TOTAL_MISMATCH` |
| Common | `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR` |

## 20. Contract Change Process

1. Propose contract changes in this document or a future OpenAPI file before implementing them.
2. Tag the affected team and dashboard/frontend consumer.
3. Avoid changing existing response fields; add optional fields when possible.
4. Use a new API version for breaking changes.
5. Add a short migration note and update examples.

This contract is a planned target. Only endpoints marked **Implemented** in Section 1 exist in the current backend at the time of writing; all `/api/v1` endpoints must be delivered and tested by their assigned feature teams.
