# Upload Integrity MVP Contract

This document is the source of truth for the image/document upload and SHA-256 integrity-verification MVP. The public `assetId` in every route is the asset UUID; database IDs must never appear in API requests or responses.

## Supported Files

| Asset type | Accepted formats |
| --- | --- |
| `IMAGE` | JPEG, PNG |
| `DOCUMENT` | PDF |

All other file formats are outside this MVP.

## Upload API

### Endpoints

- `POST /api/v1/assets/images`
- `POST /api/v1/assets/documents`

Both endpoints accept `multipart/form-data` with these fields:

| Field | Requirement |
| --- | --- |
| `file` | Required |
| `title` | Required; 1–150 characters |
| `description` | Optional; at most 2,000 characters |

### File Limits

- Images: 25 MB maximum
- Documents: 50 MB maximum
- Both limits must be configurable in `application.yml`.

### Required Upload Flow

The upload operation must:

1. Authenticate the user and obtain the current user from the JWT security context.
2. Validate all multipart fields and the applicable file-size limit.
3. Sanitize the original filename for display purposes only. It must never determine a storage path.
4. Validate the actual file signature/content rather than trusting the supplied filename extension or MIME type.
5. Confirm that the JPEG, PNG, or PDF can actually be parsed as its detected format.
6. Stream the upload to controlled temporary storage while calculating SHA-256; do not buffer the entire file solely for hashing.
7. Encode SHA-256 as exactly 64 lowercase hexadecimal characters.
8. Reject a file when its SHA-256 already exists. Return HTTP `409 Conflict` with error code `DUPLICATE_FILE` and do not store another copy.
9. Store the accepted file under a server-generated UUID filename.
10. Persist the asset with the correct `IMAGE` or `DOCUMENT` `AssetType`.
11. Set `verificationStatus` to `VERIFIED` after the file and asset are successfully persisted.
12. Create the initial `VerificationHistory` record with method `SHA256_UPLOAD`.
13. Clean up temporary files on both success and failure.

## Integrity Verification API

### Endpoint

`POST /api/v1/assets/{assetId}/verify-integrity`

### Required Verification Flow

- The asset must belong to the authenticated current owner.
- Read the stored file and recalculate its SHA-256 by streaming its bytes.
- Compare the recalculated hash with the original stored hash using exact equality.
- If equal, set the asset verification status to `VERIFIED`.
- If the stored file is different, missing, or corrupt, set the asset verification status to `REJECTED`.
- Save a `VerificationHistory` record with method `SHA256_INTEGRITY` and the corresponding result.
- Return:
  - `originalHash`
  - `currentHash` when it is available
  - `hashMatches`
  - `verificationStatus`
  - `verifiedAt`

## List, Detail, and Download API

- `GET /api/v1/assets`
- `GET /api/v1/assets/{assetId}`
- `GET /api/v1/assets/{assetId}/download`

Only asset UUIDs may be exposed as asset identifiers.
Downloads must enforce current-owner access, stream the stored file, use a safe display filename in `Content-Disposition`, and return `Cache-Control: no-store`.

## Security and Ownership

- Users may list, read, verify, and download only assets they own.
- Ownership checks must use the authenticated current user from the JWT security context, never a user identifier supplied by the client.

## Duplicate Policy

Duplicate files are not stored in this MVP. Any existing SHA-256 causes HTTP `409 Conflict` with error code `DUPLICATE_FILE`.

## Meaning of VERIFIED

`VERIFIED` means that the file passed the required upload validation and/or that its current stored bytes exactly match the SHA-256 captured during upload. It does **not** prove authorship, copyright ownership, or the truthfulness of document content.

## Non-Goals

The MVP does not include pHash or visual similarity, OCR, semantic hashes, content-authenticity detection, AI authenticity detection, authorship proof, digital signatures, blockchain, marketplace, vault functionality, or malware scanning.
