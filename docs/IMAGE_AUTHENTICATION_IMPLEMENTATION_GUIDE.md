# Image Upload, Hashing, and Duplicate Detection Guide

This guide explains how to implement the first complete Digital Asset Authentication workflow in AuthVault:

1. Accept a secure image upload.
2. Generate an exact SHA-256 digest.
3. Extract safe image metadata.
4. Generate a perceptual hash (pHash).
5. Detect exact duplicates.
6. Detect visually similar or modified copies.
7. Store the image and analysis safely.
8. Return one result: `ORIGINAL`, `MODIFIED_COPY`, `DUPLICATE`, or `UNKNOWN`.

It is written for this repository's Java 21, Spring Boot 3, Spring Data JPA, SQLite, and React stack. The broader API contract is in [API_SPECIFICATION.md](API_SPECIFICATION.md).

## 1. Scope and Definition of Done

The initial implementation should expose:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/assets/images` | Upload and analyze an image |
| `GET` | `/api/v1/assets` | List the signed-in user's assets |
| `GET` | `/api/v1/assets/{assetId}` | Read one owned asset and its result |
| `GET` | `/api/v1/assets/{assetId}/similarities` | Read duplicate/similarity evidence |
| `GET` | `/api/v1/assets/{assetId}/download` | Download an owned original |
| `DELETE` | `/api/v1/assets/{assetId}` | Soft-delete an eligible owned asset |

The feature is complete when:

- JPEG, PNG, and WebP images can be uploaded.
- The backend validates actual content, filename, and configured size.
- SHA-256 is computed from the exact uploaded bytes.
- Exact duplicates are detected across stored assets.
- pHash is computed from decoded and normalized image pixels.
- Similar images are found using Hamming distance.
- Metadata is extracted without leaking another user's private information.
- Stored files use server-generated names outside the public static directory.
- Every database read is ownership-safe.
- Temporary files are removed after success and failure.
- Unit, repository, integration, authorization, and concurrency tests pass.

## 2. Classification Model

### SHA-256 and pHash solve different problems

SHA-256 is a cryptographic digest of the exact bytes. Changing one byte normally changes the entire digest. Use it for exact duplicate and integrity detection.

pHash is a compact description of visual content. Resizing, recompressing, or slightly adjusting an image can preserve a nearby pHash. Use it only as similarity evidence.

| SHA-256 match | pHash distance | Classification | Meaning |
| --- | --- | --- | --- |
| Yes | Any | `DUPLICATE` | Exact uploaded bytes already exist |
| No | At or below configured threshold | `MODIFIED_COPY` | A visually similar asset exists |
| No | Above threshold/no candidate | `ORIGINAL` | No known exact or similar asset exists |
| Unavailable | Unavailable/unreliable | `UNKNOWN` | Analysis could not reach a reliable conclusion |

`ORIGINAL` means “not found in AuthVault's current comparison set.” It is not proof that the uploader created the image or owns its copyright.

### Recommended initial threshold

For a 64-bit pHash, begin with a Hamming-distance threshold of `8` and make it configurable:

```yaml
authvault:
  image-auth:
    phash-distance-threshold: 8
```

Validate the threshold against a real test dataset before presenting a similarity percentage as highly reliable. Store the algorithm name, version, raw distance, and threshold used for every result.

One display score can be computed as:

```text
similarityScore = 1 - (hammingDistance / hashBitLength)
```

This is a convenient normalized score, not a calibrated probability.

## 3. Recommended Processing Flow

```text
Authenticated request
       |
       v
Multipart validation ---- invalid ----> 400 / 413 / 415
       |
       v
Write controlled temporary file while computing SHA-256
       |
       +---- exact SHA match ----------> DUPLICATE
       |
       v
Decode image and extract safe metadata
       |
       v
Normalize pixels and generate pHash
       |
       v
Compare pHash candidates
       |
       +---- distance <= threshold ----> MODIFIED_COPY
       |
       +---- no nearby match ----------> ORIGINAL
       |
       v
Move original to permanent storage + save asset and match records
       |
       v
Return 201 response and always clean temporary resources
```

Do not trust or permanently store the file before validation. Do not hold the entire upload in a `byte[]` merely to calculate SHA-256; stream it.

## 4. Existing Code That Can Be Reused

The repository already contains:

- `DigitalAsset` with owner, filenames, MIME type, size, path, SHA-256, pHash, metadata, and verification state.
- `DigitalAssetRepository` with SHA and ownership lookups.
- `AssetUploadRequest`, `AssetResponse`, and `AssetVerificationResponse` DTO foundations.
- `SecurityUtils.getCurrentUser()` for resolving the authenticated user.
- `FileStorageException`, `VerificationException`, `BadRequestException`, and global exception handling.
- `server/uploads/images/` and `server/uploads/temp/` directories.

Do not serialize `DigitalAsset` directly. Its lazy entity relationships can leak data, recurse, or cause persistence errors. Map it to a response DTO.

## 5. Required Model Changes

The current `DigitalAsset.sha256Hash` is globally unique:

```java
@Column(name = "sha256_hash", nullable = false, unique = true)
private String sha256Hash;
```

That design conflicts with storing a new upload as a classified `DUPLICATE`. Choose one policy explicitly.

### Recommended policy: record every upload

Remove the unique constraint from `sha256_hash` and add a normal index. This lets the system preserve who uploaded each file, when it was uploaded, and which earlier asset it duplicated.

Add these fields to `DigitalAsset`:

```java
public enum ProcessingStatus {
    UPLOADED, PROCESSING, COMPLETED, FAILED
}

public enum AuthenticationClassification {
    ORIGINAL, MODIFIED_COPY, DUPLICATE, UNKNOWN
}

@Enumerated(EnumType.STRING)
@Column(name = "processing_status", nullable = false)
private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

@Enumerated(EnumType.STRING)
@Column(name = "authentication_classification", nullable = false)
private AuthenticationClassification authenticationClassification =
        AuthenticationClassification.UNKNOWN;

@Column(name = "classification_confidence", precision = 5, scale = 4)
private BigDecimal classificationConfidence;

@Column(name = "image_width")
private Integer imageWidth;

@Column(name = "image_height")
private Integer imageHeight;

@Column(name = "hash_algorithm_version", nullable = false)
private String hashAlgorithmVersion;

@Column(name = "deleted_at")
private LocalDateTime deletedAt;
```

Create a match entity rather than storing one match inside JSON:

```java
@Entity
@Table(name = "asset_similarity_matches",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_asset_match_pair_algorithm",
           columnNames = {"source_asset_id", "matched_asset_id", "algorithm_version"}
       ))
public class AssetSimilarityMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_asset_id", nullable = false)
    private DigitalAsset sourceAsset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matched_asset_id", nullable = false)
    private DigitalAsset matchedAsset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType matchType; // EXACT or PERCEPTUAL

    @Column(name = "hamming_distance")
    private Integer hammingDistance;

    @Column(name = "similarity_score", precision = 5, scale = 4)
    private BigDecimal similarityScore;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

For team development, use versioned database migrations instead of relying only on `ddl-auto=update`. If migrations are deferred during the capstone, document each schema change in `server/database/schema.sql` and back up the SQLite database before modifying constraints.

### Minimum indexes

```sql
CREATE INDEX idx_assets_sha256 ON digital_assets(sha256_hash);
CREATE INDEX idx_assets_phash ON digital_assets(perceptual_hash);
CREATE INDEX idx_assets_owner_upload ON digital_assets(owner_id, upload_date);
CREATE INDEX idx_assets_classification ON digital_assets(authentication_classification);
CREATE INDEX idx_similarity_source ON asset_similarity_matches(source_asset_id);
```

SQLite cannot efficiently query arbitrary Hamming distance from a hex string using ordinary indexes. For the capstone dataset, loading only eligible pHashes and comparing them in Java is acceptable. At production scale, use bucketed pHash segments, locality-sensitive hashing, or a suitable similarity index.

## 6. Configuration

Add environment-aware settings to `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 26MB

authvault:
  storage:
    image-directory: ${AUTHVAULT_IMAGE_DIR:uploads/images}
    temp-directory: ${AUTHVAULT_TEMP_DIR:uploads/temp}
  image-auth:
    allowed-mime-types:
      - image/jpeg
      - image/png
      - image/webp
    phash-distance-threshold: 8
    algorithm-version: phash-dct-1.0
```

Resolve and normalize configured directories at startup. Reject any resolved destination that escapes the configured root.

## 7. Package and Class Plan

The following layout keeps controllers thin and algorithm code independently testable:

```text
com.authvault.asset
├── controller/
│   └── ImageAssetController.java
├── dto/
│   ├── ImageUploadResponse.java
│   ├── AssetDetailResponse.java
│   └── SimilarityResponse.java
├── hashing/
│   ├── Sha256Service.java
│   ├── PerceptualHashService.java
│   └── DctPerceptualHashService.java
├── metadata/
│   ├── ImageMetadata.java
│   └── ImageMetadataExtractor.java
├── storage/
│   ├── AssetStorageService.java
│   └── LocalAssetStorageService.java
├── validation/
│   └── ImageUploadValidator.java
├── service/
│   └── ImageAuthenticationService.java
└── repository/
    └── AssetSimilarityMatchRepository.java
```

The project can keep its current global `controller`, `dto`, and `service` folders if the team prefers. Use the same responsibilities even if package paths differ.

## 8. Upload Request and Controller

The existing `AssetUploadRequest` can be used with `@ModelAttribute` because it contains a `MultipartFile`.

```java
@RestController
@RequestMapping("/api/v1/assets")
public class ImageAssetController {
    private final ImageAuthenticationService imageAuthenticationService;

    public ImageAssetController(ImageAuthenticationService imageAuthenticationService) {
        this.imageAuthenticationService = imageAuthenticationService;
    }

    @PostMapping(
        value = "/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @Valid @ModelAttribute AssetUploadRequest request) {
        ImageUploadResponse result = imageAuthenticationService.uploadAndAnalyze(request);

        ApiResponse<ImageUploadResponse> response = ApiResponse.<ImageUploadResponse>builder()
                .success(true)
                .message("Image uploaded and analyzed successfully")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

Use `@ModelAttribute`, not `@RequestBody`, for a multipart request containing a file.

Example request:

```bash
curl -X POST http://localhost:8080/api/v1/assets/images \
  -H "Authorization: Bearer YOUR_JWT" \
  -F "title=Artwork proof" \
  -F "description=Original source image" \
  -F "file=@/absolute/path/to/artwork.png"
```

## 9. File Validation

Validation must happen in layers.

### Basic multipart checks

```java
public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new BadRequestException("An image file is required");
    }
    if (file.getSize() > maxBytes) {
        throw new BadRequestException("Image exceeds the maximum allowed size");
    }
}
```

### Filename handling

- Use the original filename only as display metadata.
- Normalize it with `Paths.get(name).getFileName().toString()`.
- Remove control characters.
- Set a reasonable maximum length.
- Never concatenate it into a storage path.
- Generate a stored filename such as `<UUID>.png`.

### Content validation

`MultipartFile.getContentType()` is supplied by the client and is not authoritative. Detect content from magic bytes with a trusted content detector, then verify that a safe image decoder can read it.

At minimum:

```java
try (InputStream input = Files.newInputStream(tempFile)) {
    BufferedImage image = ImageIO.read(input);
    if (image == null) {
        throw new UnsupportedMediaTypeException("File is not a supported image");
    }
}
```

Default Java `ImageIO` support varies for WebP. Either add a maintained WebP ImageIO plugin or initially allow only formats confirmed by `ImageIO.getReaderMIMETypes()` in the deployed runtime. Never advertise WebP until an integration test proves decoding works.

Also protect against decompression bombs. Reject unreasonable pixel counts even when compressed file size is small:

```text
width > 0
height > 0
width * height <= configured maximum, for example 100 megapixels
```

## 10. Streaming SHA-256 Generation

Use Java's standard `MessageDigest`. Compute the digest while copying the upload to a controlled temporary file so the request stream is consumed only once.

```java
@Service
public class Sha256Service {
    public HashAndSize copyAndHash(InputStream source, Path destination) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;

            try (DigestInputStream input = new DigestInputStream(source, digest);
                 OutputStream output = Files.newOutputStream(
                     destination,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE
                 )) {
                size = input.transferTo(output);
            }

            return new HashAndSize(HexFormat.of().formatHex(digest.digest()), size);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new FileStorageException("Could not process uploaded image", exception);
        }
    }

    public record HashAndSize(String sha256, long size) {}
}
```

SHA-256 output must be a lowercase, 64-character hexadecimal string. Add a unit test with a known test vector:

```text
SHA-256("abc") =
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

## 11. Exact Duplicate Detection

Change the repository query to return multiple records if duplicate uploads are retained:

```java
List<DigitalAsset> findAllBySha256HashAndDeletedAtIsNull(String sha256Hash);
```

Detect duplicates immediately after hashing and before expensive pHash work:

```java
List<DigitalAsset> exactMatches = repository
        .findAllBySha256HashAndDeletedAtIsNull(hashAndSize.sha256());

AuthenticationClassification classification = exactMatches.isEmpty()
        ? AuthenticationClassification.UNKNOWN
        : AuthenticationClassification.DUPLICATE;
```

Even for an exact duplicate, decode the uploaded file enough to ensure it is a supported image before committing a record. Do not reveal another user's identity, filename, description, private metadata, or download URL. The response may safely state that an existing exact match was found.

### Concurrency requirement

Two identical files can arrive at the same time. A read-then-insert check alone can race. Because duplicates are intentionally permitted, both records may be saved, but both analyses must converge on a deterministic relationship. After inserting, query for the earliest other matching record and create the match row. Use one transaction for asset and match persistence.

If the product instead rejects exact duplicates, retain the unique database constraint and translate the constraint violation to `409 Conflict`. Do not depend only on `existsBySha256Hash`.

## 12. Metadata Extraction

Start with metadata available from the decoded image:

```java
public record ImageMetadata(
    int width,
    int height,
    String mimeType,
    String format,
    String colorModel,
    boolean hasAlpha,
    Map<String, String> exif
) {}
```

Basic extraction:

```java
BufferedImage image = ImageIO.read(tempFile.toFile());
ImageMetadata metadata = new ImageMetadata(
    image.getWidth(),
    image.getHeight(),
    detectedMimeType,
    detectedFormat,
    image.getColorModel().getColorSpace().toString(),
    image.getColorModel().hasAlpha(),
    safeExif
);
```

For EXIF/IPTC/XMP, add a dedicated metadata-extraction library and wrap it behind `ImageMetadataExtractor`. Keep the library out of controllers so it can be changed later.

Classify metadata fields:

- Safe/private: camera make/model, orientation, capture date, software.
- Sensitive/private by default: GPS coordinates, owner name, device serial number, embedded comments.
- Public: width, height, normalized format, file size, safe color information.

Serialize structured metadata with Jackson, not manual string concatenation:

```java
String metadataJson = objectMapper.writeValueAsString(metadata);
```

Metadata extraction failure should usually produce an empty or partial metadata object plus a warning. It should not necessarily fail hashing and duplicate detection.

## 13. Perceptual Hash Generation

Use one documented algorithm consistently. A conventional 64-bit DCT pHash pipeline is:

1. Decode the image.
2. Correct EXIF orientation.
3. Remove alpha by compositing against a defined background.
4. Convert to grayscale.
5. Resize to `32 x 32` with a fixed interpolation method.
6. Compute the 2D discrete cosine transform.
7. Read the top-left `8 x 8` low-frequency coefficients.
8. Exclude or consistently handle the DC coefficient.
9. Compare each coefficient with the median.
10. Encode the resulting 64 bits as 16 lowercase hexadecimal characters.

Interface:

```java
public interface PerceptualHashService {
    PerceptualHashResult generate(BufferedImage image);

    int hammingDistance(String firstHexHash, String secondHexHash);

    record PerceptualHashResult(
        String hash,
        int bitLength,
        String algorithm,
        String version
    ) {}
}
```

Hamming-distance implementation:

```java
public int hammingDistance(String firstHexHash, String secondHexHash) {
    if (firstHexHash.length() != secondHexHash.length()) {
        throw new IllegalArgumentException("pHash lengths must match");
    }

    BigInteger first = new BigInteger(firstHexHash, 16);
    BigInteger second = new BigInteger(secondHexHash, 16);
    return first.xor(second).bitCount();
}
```

Use deterministic tests. The same decoded pixels must always produce the same pHash on every supported environment. If an external library is chosen, pin its version and store that version with each result.

## 14. Similarity Detection

For the capstone-sized dataset, add a projection that returns only identifiers and pHashes:

```java
public interface AssetHashProjection {
    Long getId();
    String getUuid();
    String getPerceptualHash();
}

@Query("""
    select a.id as id, a.uuid as uuid, a.perceptualHash as perceptualHash
    from DigitalAsset a
    where a.assetType = com.authvault.entity.DigitalAsset.AssetType.IMAGE
      and a.perceptualHash is not null
      and a.deletedAt is null
""")
List<AssetHashProjection> findImageHashes();
```

Compare without loading files or full entities:

```java
List<SimilarityCandidate> candidates = repository.findImageHashes().stream()
    .map(candidate -> new SimilarityCandidate(
        candidate.getUuid(),
        perceptualHashService.hammingDistance(
            uploadedHash,
            candidate.getPerceptualHash()
        )
    ))
    .filter(candidate -> candidate.distance() <= configuredThreshold)
    .sorted(Comparator.comparingInt(SimilarityCandidate::distance))
    .limit(10)
    .toList();
```

Rules:

- Exclude the asset being analyzed during reanalysis.
- Prefer exact SHA classification over pHash classification.
- Record all candidates within the threshold or at least the closest configured number.
- Return only safe match fields when another user owns the match.
- Do not compare raw filenames or user-provided metadata as visual evidence.

## 15. Safe File Storage

Storage interface:

```java
public interface AssetStorageService {
    StoredAsset commit(Path validatedTempFile, String extension);
    Resource loadOwnedAsset(DigitalAsset asset);
    void delete(String storageKey);

    record StoredAsset(String storageKey, Path path) {}
}
```

Permanent filename:

```java
String storageKey = UUID.randomUUID() + "." + validatedExtension;
Path destination = imageRoot.resolve(storageKey).normalize();

if (!destination.startsWith(imageRoot)) {
    throw new FileStorageException("Invalid storage destination");
}
```

Prefer an atomic move from the temporary location when the filesystem supports it:

```java
Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE);
```

Fall back safely when atomic moves are unsupported. Set restrictive filesystem permissions where supported. The `uploads` directory must not be exposed by Spring static-resource mappings.

### Database/file consistency

The filesystem does not participate in the database transaction. Use a compensating cleanup pattern:

1. Create a temporary file.
2. Analyze it completely.
3. Move it to permanent storage.
4. Save database records in a transaction.
5. If database save fails, delete the newly committed permanent file.
6. Always remove any remaining temporary file in `finally`.

For higher reliability later, store an upload state and use a cleanup job for abandoned files.

## 16. Orchestration Service

Keep the workflow in a service, not the controller:

```java
@Service
public class ImageAuthenticationService {
    @Transactional
    public ImageUploadResponse uploadAndAnalyze(AssetUploadRequest request) {
        User user = SecurityUtils.getCurrentUser();
        Path tempFile = tempStorage.create();
        StoredAsset committed = null;

        try {
            validator.validate(request.getFile());

            HashAndSize hash = sha256Service.copyAndHash(
                request.getFile().getInputStream(), tempFile
            );

            DetectedImage detected = validator.decodeAndValidate(tempFile);
            ImageMetadata metadata = metadataExtractor.extract(tempFile, detected);
            List<DigitalAsset> exactMatches = assetRepository
                .findAllBySha256HashAndDeletedAtIsNull(hash.sha256());

            PerceptualHashResult perceptual = perceptualHashService
                .generate(detected.image());
            List<SimilarityCandidate> similar = similarityService
                .findMatches(perceptual.hash(), configuredThreshold);

            ClassificationDecision decision = classifier.classify(
                exactMatches, similar
            );

            committed = assetStorage.commit(tempFile, detected.extension());
            DigitalAsset saved = saveAssetAndMatches(
                request, user, hash, metadata, perceptual,
                decision, committed
            );

            return mapper.toUploadResponse(saved, decision);
        } catch (IOException exception) {
            cleanupCommittedFile(committed);
            throw new FileStorageException("Could not process uploaded image", exception);
        } catch (RuntimeException exception) {
            cleanupCommittedFile(committed);
            throw exception;
        } finally {
            tempStorage.deleteQuietly(tempFile);
        }
    }
}
```

The example shows responsibilities and ordering, not a copy-paste-complete class. Individual services should be unit tested independently.

## 17. Response Contract

Use an explicit response DTO:

```java
public record ImageUploadResponse(
    String assetId,
    String title,
    String assetType,
    String originalFilename,
    String mimeType,
    long fileSize,
    String processingStatus,
    String classification,
    BigDecimal confidence,
    String sha256,
    String perceptualHash,
    SafeImageMetadataResponse metadata,
    MatchSummaryResponse exactMatch,
    List<MatchSummaryResponse> similarMatches,
    LocalDateTime uploadedAt
) {}
```

Example original response:

```json
{
  "success": true,
  "message": "Image uploaded and analyzed successfully",
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
    "sha256": "4c4b6a3b7a...",
    "perceptualHash": "a17fd2239910bc4e",
    "metadata": {
      "width": 1920,
      "height": 1080,
      "format": "PNG",
      "hasAlpha": true
    },
    "exactMatch": null,
    "similarMatches": [],
    "uploadedAt": "2026-08-07T10:30:00"
  },
  "timestamp": "2026-08-07T10:30:00"
}
```

Example duplicate evidence visible to the uploader:

```json
{
  "classification": "DUPLICATE",
  "confidence": 1.0,
  "exactMatch": {
    "relationship": "EXACT_MATCH",
    "matchedAssetId": "ast_2dc9e802",
    "ownedByCurrentUser": true,
    "sha256Matched": true,
    "hammingDistance": 0,
    "similarityScore": 1.0
  }
}
```

If the match belongs to someone else, `matchedAssetId` may need to be omitted or replaced with an opaque match reference according to the privacy policy.

## 18. Download Endpoint

Resolve by public UUID and current owner:

```java
DigitalAsset asset = repository
    .findByUuidAndCurrentOwnerIdAndDeletedAtIsNull(assetId, currentUser.getId())
    .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
```

Return:

```text
Content-Type: stored validated MIME type
Content-Disposition: attachment; filename="safe-original-name.png"
Cache-Control: private, no-store
X-Content-Type-Options: nosniff
```

Do not accept a path from the client. Resolve storage only from the authorized database record.

## 19. Error Mapping

Add or reuse explicit errors:

| Situation | HTTP status | Code/message |
| --- | --- | --- |
| Empty file | `400` | `IMAGE_REQUIRED` |
| Invalid title | `400` | `VALIDATION_FAILED` |
| File exceeds maximum | `413` | `IMAGE_TOO_LARGE` |
| MIME/decoder not allowed | `415` | `UNSUPPORTED_IMAGE` |
| Excessive dimensions | `422` | `IMAGE_DIMENSIONS_UNSAFE` |
| Image decode/hash failure | `422` or `500` | `IMAGE_ANALYSIS_FAILED` |
| Asset unavailable/private | `404` | `ASSET_NOT_FOUND` |
| Asset cannot be deleted | `409` | `ASSET_ALREADY_LISTED` |
| Storage failure | `500` | Generic public message; log safe internal cause |

The current `ErrorResponse` does not include a machine-readable `code`. Adding it will make frontend handling and teammate integrations more reliable.

## 20. Test Plan

### SHA-256 unit tests

- Known `abc` test vector.
- Empty-byte test vector.
- Same bytes always return same hash.
- One changed byte produces a different hash.
- Large stream is processed without loading it all into memory.

### pHash unit tests

- Same decoded image returns distance `0`.
- Resized copy stays within the chosen threshold.
- JPEG recompression stays near the original.
- Small brightness/contrast change has a nearby hash.
- Unrelated images exceed the threshold.
- Hamming distance is symmetric.
- Invalid-length hashes are rejected.
- Transparent-image normalization is deterministic.

### Metadata tests

- Dimensions and format are correct.
- Rotated EXIF orientation is normalized consistently.
- Missing EXIF does not fail analysis.
- GPS and device serial fields never appear in public DTOs.
- Malformed metadata does not crash the upload.

### Repository tests

- Multiple records can share SHA-256 when duplicate uploads are retained.
- Exact-match query ignores soft-deleted records if required by policy.
- Hash projection returns only eligible image assets.
- Asset lookup is restricted by current owner.
- Similarity pair uniqueness is enforced.

### Controller integration tests

- Valid authenticated PNG returns `201` and `ORIGINAL`.
- Uploading the identical file again returns `DUPLICATE`.
- Uploading resized/recompressed fixture returns `MODIFIED_COPY`.
- Uploading a different fixture returns `ORIGINAL`.
- Missing JWT returns `401`.
- Empty upload returns `400`.
- Oversized upload returns `413`.
- Text renamed to `.png` returns `415`.
- Corrupt image returns `415` or `422` consistently.
- One user cannot read or download another user's private asset.
- A failed database write does not leave a permanent orphan file.
- A failed analysis does not leave a temporary file.

### Concurrency tests

- Two simultaneous identical uploads both produce consistent duplicate relationships.
- Reanalysis cannot create duplicate match rows.
- Download during deletion follows a defined locking/soft-delete policy.

Keep image fixtures small and license-safe under `server/src/test/resources/images/`:

```text
original.png
exact-copy.png
resized.png
recompressed.jpg
brightness-adjusted.png
unrelated.png
corrupt.png
fake-image.png
```

## 21. Frontend Integration

Create an API service instead of calling Axios directly inside the page:

```javascript
import api from './api'

export async function uploadImage({ file, title, description }, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title)
  if (description) formData.append('description', description)

  const response = await api.post('/v1/assets/images', formData, {
    onUploadProgress: ({ loaded, total }) => {
      if (total) onProgress?.(Math.round((loaded / total) * 100))
    },
  })

  return response.data.data
}
```

The UI should include:

- Drag/drop and file picker.
- Client-side size and type hints, while treating server validation as authoritative.
- Upload progress separate from server analysis progress.
- Preview created with `URL.createObjectURL`, revoked during cleanup.
- Clear result badge: Original, Modified Copy, Duplicate, or Unknown.
- SHA-256 with a copy button.
- Safe metadata summary.
- Match evidence without exposing another user's private content.
- Retry for network failure, but not automatic duplicate uploads.

Do not send the JWT manually; the existing Axios interceptor adds it.

## 22. Synchronous First, Asynchronous Later

For a capstone-sized deployment, implement synchronous processing first if typical images complete quickly. Return `201 Created` after analysis.

Move to asynchronous processing when OCR, large files, or pHash comparisons cause noticeable latency:

1. Save asset as `QUEUED`.
2. Return `202 Accepted` with a status URL.
3. Process using a managed executor or queue.
4. Update status to `COMPLETED` or `FAILED`.
5. Let the frontend poll `GET /assets/{assetId}/status`.

Never start unmanaged background threads directly from a controller.

## 23. Observability and Privacy

Log safe operational data:

- Request/correlation ID.
- Authenticated user UUID, not password or token.
- Asset UUID.
- File size and detected MIME.
- Processing duration by stage.
- Classification and algorithm version.
- Failure category.

Do not log:

- JWTs.
- File bytes.
- Full metadata JSON.
- GPS/device identifiers.
- User-supplied secret or private content.
- Absolute server storage paths in public errors.

Useful metrics include upload count, rejected upload count, processing duration, classification count, storage failures, and pHash candidate count.

## 24. Recommended Implementation Order

### Phase 1: safe exact duplicate detection

1. Add configuration and storage-root validation.
2. Change SHA uniqueness policy and add classifications.
3. Implement multipart validation.
4. Implement streaming temporary copy and SHA-256.
5. Implement controlled permanent storage.
6. Add upload, list, detail, and download endpoints.
7. Add exact duplicate response and tests.

### Phase 2: visual similarity

1. Implement deterministic decode and normalization.
2. Implement or integrate versioned 64-bit pHash.
3. Add hash projection and Hamming comparison.
4. Add similarity-match persistence.
5. Add `MODIFIED_COPY` classification and tests.

### Phase 3: metadata and dashboard

1. Add safe metadata extraction and redaction.
2. Add asset filters and pagination.
3. Connect dashboard totals to real classification counts.
4. Replace mock dashboard activity with persisted upload/authentication events.

### Phase 4: hardening

1. Add malware scanning integration.
2. Add rate limiting.
3. Add abandoned-file cleanup.
4. Add asynchronous processing when required.
5. Benchmark similarity detection and introduce candidate indexing when necessary.

## 25. Team Handoff Checklist

Before opening a pull request, the feature owner should provide:

- Database/model changes and migration notes.
- Controller, service, repository, mapper, and DTO implementation.
- Exact endpoint examples matching the API contract.
- Unit and integration tests with image fixtures.
- Security evidence for unauthorized cross-user access.
- Confirmation that temp/permanent files are cleaned on failure.
- Chosen pHash algorithm, version, threshold, and test dataset results.
- Updated README/API specification if the contract changed.
- Frontend service integration instructions.

The reviewer should verify that duplicate detection is backed by a database rule or concurrency-safe transaction strategy, not only a pre-insert Java check.

## 26. Common Mistakes to Avoid

- Treating filename extensions or client MIME types as trustworthy.
- Reading the complete file into heap memory.
- Using SHA-256 for visual similarity.
- Calling a pHash match proof of authorship.
- Keeping `sha256_hash` unique while trying to save duplicate uploads.
- Returning another user's matching image or metadata.
- Storing files under `src/main/resources/static`.
- Using the original filename as the storage filename.
- Manually building metadata JSON.
- Comparing every full entity or image file instead of compact hash projections.
- Hard-coding a similarity threshold with no algorithm version.
- Saving the database row and filesystem file without compensating cleanup.
- Returning JPA entities from controllers.
- Adding background threads without lifecycle management.
- Showing a normalized Hamming score as a calibrated probability.

Following this guide produces a secure, testable first vertical slice that the dashboard, verification module, vault, and marketplace can reuse.
