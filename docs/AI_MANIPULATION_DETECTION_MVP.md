# AI Manipulation Detection MVP Contract

This document is the source of truth for AuthVault's image-forensics architecture. It supersedes any earlier advanced-verification design where that design conflicts with this contract. It does not replace the working upload-integrity or asset-management contracts.

The primary product goal is to detect and analyze evidence that an image may have been AI-generated or manipulated. AuthVault produces a modular forensic evidence report, not a universal fake/real classifier and not legal or factual proof.

Current evidence pipeline:

```text
SHA-256 integrity
  -> pHash retrieval
  -> known-original comparison/change map
  -> UFD global synthetic-image evidence
  -> TruFor local general-manipulation evidence
  -> evidence fusion (next phase; not implemented)
```

## 1. Distinct Evidence Concepts

### A. File Integrity

The existing SHA-256 workflow asks: **Are the stored bytes identical to the bytes recorded at upload?**

It detects changes after upload. It does not determine whether an image was manipulated before upload.

### B. Perceptual Retrieval

A deterministic 64-bit pHash asks: **Does this image appear visually related to one of this user's previous images?**

pHash is a retrieval and similarity signal. It is not an AI detector and must not be the final manipulation decision.

### C. Known-Original Comparison

When pHash finds a plausible previous image, the candidate and new image may be geometrically normalized, aligned, and compared to produce a change/localization map. This asks: **Where do these two related images differ?**

A detected difference does not by itself establish that AI caused the change.

### D. AI-Generated Image Detection

A learned global detector estimates whether an entire image contains forensic evidence associated with synthetic generation. Its output is statistical evidence or probability, not proof.

### E. AI or General Manipulation Localization

A learned forensic model estimates whether local regions appear manipulated. A capable model may return a manipulation score, pixel/region mask, and a genuine reliability score. Unsupported outputs must remain absent rather than being invented.

### F. Provenance

Future C2PA/Content Credentials analysis provides an independent cryptographic provenance signal. Valid credentials may identify generator/edit information. Absence of C2PA must never be interpreted as evidence that an image is fake.

### G. OCR

OCR is primarily a document-verification feature and follows the core image-forensics pipeline. It is not part of this MVP.

## 2. Decision Terminology

Future analysis-level decisions are:

- `LIKELY_UNMODIFIED`
- `REVIEW`
- `LIKELY_MANIPULATED`
- `INCONCLUSIVE`

These are forensic analysis decisions, not legal or factual proof. The existing database `VerificationStatus` values remain unchanged and serve their current integrity workflow.

## 3. Target Evidence Report

The future public DTO is conceptually named `ForensicAnalysisReport` and contains:

- `assetId`: public asset UUID.
- `integrity`: `performed`, `hashMatches`, and `status`.
- `previousImageComparison`: `performed`, `candidateFound`, public `candidateAssetId`, `perceptualHashDistance`, `differenceDetected`, `changedAreaRatio`, and an optional future change-map reference.
- `aiGenerationAnalysis`: `performed`, `modelName`, `modelVersion`, raw logit/score, decision threshold, model signal, calibration status, optional calibrated synthetic probability, and technical status.
- `manipulationAnalysis`: `performed`, `modelName`, `modelVersion`, raw `manipulationScore`, decision threshold, technical signal, localization availability, suspicious-area ratios, anomaly-map PNG, reliability-map PNG, manipulation-suspicion mask PNG, and status.
- `provenance`: `performed`, `credentialsPresent`, `credentialsValid`, and generator/edit information only when cryptographically available.
- `decision`: one of the four analysis decisions above.
- `reasonCodes[]`.
- `analyzedAt`.

Public reports must never expose database IDs, filesystem paths, internal AI-service URLs, model filesystem locations, or Python exceptions.

## 4. Evidence Interpretation Rules

| Evidence | Safe interpretation |
| --- | --- |
| SHA-256 mismatch | Strong evidence that stored bytes changed after the upload hash was recorded. |
| pHash match | Evidence of visual similarity only. |
| Image difference | Evidence that related images differ. |
| AI-model probability | Statistical detector evidence tied to a named model and version. |
| Manipulation localization | An estimated suspicious region, subject to model reliability and limitations. |
| Valid C2PA | Independent cryptographic provenance evidence for the claims actually signed. |

None of these signals individually proves authorship, truthfulness, copyright ownership, or universal authenticity.

## 5. Service Architecture and Security Boundary

```text
Browser
  | public JWT request
  v
Spring Boot (main application, authorization, assets, database)
  | internal HTTP with controlled image bytes/inputs
  v
FastAPI AI service (stateless inference boundary)
```

Spring Boot remains the public application/API and owns authentication, authorization, asset ownership, persistence, and safe storage access. The browser must not call the AI service directly.

The AI service:

- Exposes internal service endpoints only.
- Does not validate public JWTs.
- Does not access users, wallets, repositories, SQLite, or any application database.
- Does not receive or expose stored asset paths.
- Receives controlled image bytes or controlled analysis inputs from Spring.
- Starts without model weights and never silently downloads weights during requests.

AI-service failure must not affect authentication, upload, SHA-256 verification, asset listing/detail, download, delete, or verification history. Learned-model integration remains disabled by default and runs only through the explicit owner-authenticated analysis endpoint. Known-original comparison is invoked only by its explicit owner-authenticated endpoint and reports disabled or unavailable AI-service conditions without fabricating comparison results.

## 6. Current Internal AI-Service Contract

### `GET /health`

Response when no detector is configured:

```json
{
  "status": "ok",
  "service": "authvault-ai",
  "modelsReady": false,
  "models": {
    "aiGeneration": {
      "configured": false,
      "ready": false,
      "modelName": "UNIVERSAL_FAKE_DETECT",
      "modelVersion": "UFD_CVPR2023_CLIP_VIT_L14_FC_V1"
    },
    "manipulation": {
      "configured": false,
      "ready": false,
      "modelName": "TRUFOR",
      "modelVersion": "TRUFOR_CVPR2023_RELEASED_V1"
    }
  }
}
```

`modelsReady` means at least one learned detector is configured and every enabled detector is ready. Disabled UFD or TruFor detectors are not counted as configured requirements.

### `POST /v1/analyze/image`

This internal endpoint accepts one multipart `file` containing a non-empty JPEG or PNG. It enforces the configured maximum byte size, verifies magic bytes, and requires Pillow to decode the actual image. It does not trust the supplied MIME type.

Response when UFD is not configured:

```json
{
  "analysisVersion": "1",
  "aiGeneration": {
    "performed": false,
    "status": "MODEL_NOT_CONFIGURED"
  },
  "manipulation": {
    "performed": false,
    "status": "MODEL_NOT_CONFIGURED"
  }
}
```

No probability is returned when analysis was not performed. Missing models do not imply a low manipulation or synthetic-generation probability.

When UFD is ready, `aiGeneration` returns `rawLogit`, `rawSyntheticScore`, the configured `decisionThreshold`, `REAL_LEANING` or `SYNTHETIC_LEANING`, calibration status, and a calibrated probability only when a matching calibration artifact is valid. When TruFor is ready, `manipulation` independently returns its raw image-level score, signal, anomaly/reliability maps, suspicious-region mask, and area ratios. Either detector may succeed while the other reports a controlled unavailable or processing status.

Validation status mapping:

- Empty upload: `400`.
- Oversized upload: `413`.
- Unsupported actual format: `415`.
- Corrupt or undecodable JPEG/PNG: `422`.

### `POST /v1/compare/images`

This internal multipart endpoint accepts `reference` and `target` JPEG/PNG parts. Each input is independently bounded by compressed byte size and decoded pixel count, checked by magic bytes, decoded with Pillow, normalized for EXIF orientation, and converted to RGB. Supplied MIME types and filenames are not trusted.

The service attempts ORB feature matching, Hamming-distance ratio filtering, RANSAC homography estimation, and warping of the target into the reference coordinate system. It reports keypoint, good-match, homography-inlier, and inlier-ratio metrics. Low-feature or low-quality alignment uses a clearly reported `FALLBACK_RESIZE`; it is never reported as successful homography alignment.

The deterministic difference stage uses a small Gaussian blur, absolute RGB difference reduced to a scalar map, configured magnitude thresholding, valid-overlap exclusion, morphological cleanup, and connected-component area filtering. It returns:

- `changedAreaRatio`: changed pixels divided by valid comparable pixels.
- `meanAbsoluteDifference`: mean scalar pixel difference over valid overlap.
- `structuralSimilarity`: `null`; SSIM is not calculated in this phase.
- A bounded Base64-encoded binary PNG mask in reference-image dimensions, where white marks material visual differences and black marks unchanged or non-comparable pixels.

Technical statuses are `COMPLETED`, `NO_VALID_OVERLAP`, and `PROCESSING_FAILED`; alignment statuses are `ALIGNED`, `FALLBACK_RESIZE`, and `ALIGNMENT_FAILED`. The endpoint does not access application storage, paths, users, JWTs, or the database.

## 7. Replaceable Detector Slots

### Detector A: Global AI-Generation Detector

Purpose: estimate whether an entire image appears synthetically generated.

`AiGenerationDetector.analyze(image)` is implemented by `UniversalFakeDetector`. It returns raw UFD evidence, model identity, threshold signal, optional calibrated probability, and a technical status.

### Detector B: Manipulation Localizer

Purpose: detect and localize suspicious edited regions.

`ManipulationDetector.analyze(image)` is implemented by `TruForDetector`. It returns TruFor's raw manipulation score, independent anomaly and reliability maps, an AuthVault-derived binary manipulation-suspicion mask, area ratios, model identity, and technical status.

### Detector C: Known-Original Comparator

Purpose: when pHash retrieves a related previous image, align the pair and calculate a change map. The deterministic ORB/homography/visual-difference implementation is now available through the internal comparison endpoint; it is not a learned manipulation detector.

The lightweight `ForensicAnalysisService` depends on detector interfaces. It constructs UFD and TruFor once through the cached service lifecycle. Each detector owns a separate concurrency guard, and one detector's technical failure does not erase the other's evidence. API routes do not change when implementations are swapped.

No weights are vendored in this MVP. UFD was selected for the global detector slot and TruFor for the restricted nonprofit general-localization slot; future detector selection still requires:

- License review.
- A reproducible model name and version.
- Documented inference requirements.
- Benchmark and unseen-generator evaluation.
- JPEG/compression robustness evaluation.
- False-positive evaluation on real photographs.
- Manipulated-region evaluation where applicable.

Every performed detector response must identify `modelName` and `modelVersion`. AuthVault must never persist an unexplained score and must support rerunning newer detector versions later.

## 8. Implemented pHash Retrieval Phase

Spring now calculates and persists a deterministic 64-bit pHash for every new `IMAGE` upload after content validation and SHA-256 duplicate detection. The implementation converts the decoded image to luminance, deterministically resizes it to 32×32, computes a two-dimensional DCT, takes the lowest 8×8 coefficients, excludes the DC coefficient from the median calculation, and serializes the resulting 64 bits as exactly 16 lowercase hexadecimal characters. `DOCUMENT` assets do not receive a pHash.

The nullable `DigitalAsset.perceptualHash` column supports historical images and is indexed but not unique. When a historical target or candidate is first needed and has no pHash, Spring loads it through controlled asset storage, calculates the hash, and persists it. A missing, corrupt, or malformed historical candidate is logged without its storage path and skipped; a target that cannot be analyzed returns a controlled failure. There is no startup scan or automatic batch backfill.

`GET /api/v1/assets/{assetId}/similar-images` is authenticated, accepts only the public asset UUID, resolves the target through the existing current-owner lookup, and supports `IMAGE` assets only. It compares the target only with earlier `IMAGE` assets belonging to the same current owner, ranks them by ascending Hamming distance, and returns at most the configured candidate limit. Responses expose safe asset metadata, the target pHash, Hamming distance, and one of:

- `EXACT_VISUAL_HASH` for distance `0`.
- `NEAR_DUPLICATE` through the configured review threshold.
- `POSSIBLE_MATCH` through the configured possible-match threshold.
- `NO_MATCH` above the possible-match threshold.

Configuration lives under `authvault.verification.phash`. Defaults are enabled, review threshold `6`, possible-match threshold `14`, and maximum candidates `5`. Thresholds are heuristic retrieval bands that require calibration against representative AuthVault images. They never change `VerificationStatus`, reject an image, or represent AI confidence.

## 9. Implemented Known-Original Comparison

`POST /api/v1/assets/{assetId}/compare-known-original` resolves an owner-scoped `IMAGE` target, reuses persisted pHash candidate ranking, and automatically selects the closest previous candidate whose match band is `EXACT_VISUAL_HASH`, `NEAR_DUPLICATE`, or `POSSIBLE_MATCH`. `NO_MATCH` is never treated as a known original. Exact pHash equality still invokes byte-independent visual comparison because pHash equality does not imply exact bytes.

Spring re-resolves the selected candidate by public UUID and authenticated current owner, loads both images through controlled storage, and sends only their streams and safe detected media types to the internal AI-service client. It sends no JWT, user identity, database ID, storage key, filename metadata, or filesystem path.

When no plausible candidate exists, Spring returns a successful controlled result with `candidateFound=false`, `comparisonPerformed=false`, and reason `NO_KNOWN_ORIGINAL`, without calling the AI service. Disabled and unavailable AI-service states return `AI_SERVICE_DISABLED` or `AI_SERVICE_UNAVAILABLE` without invented metrics. Successful responses expose only public asset UUIDs, safe candidate display metadata, pHash distance/band, technical alignment metrics, visual-difference metrics, the temporary Base64 PNG change mask, and analysis time. Masks are not persisted.

Comparison configuration is centralized in the AI service and includes compressed input size, decoded pixel limit, output-mask byte limit, ORB feature/match settings, minimum good matches/inliers/inlier ratio, RANSAC reprojection threshold, pixel-difference threshold, minimum region area, and blur/morphology kernel sizes.

Visual differences are evidence that related images differ. They are not proof of AI manipulation, authorship, authenticity, or falsity.

## 10. Spring-to-AI Contract

Spring configuration uses `authvault.ai-service` with `enabled=false` by default, an internal base URL, and bounded connection/read timeouts. A replaceable Spring client maps the health and analysis response DTOs and converts disabled or unavailable service conditions into controlled client results.

The client is not called by upload, SHA-256 verification, asset reads, or background workflows. It is called only by explicit owner-authenticated known-original comparison and AI-generation analysis actions. Internal AI-service URLs and low-level failures must not appear in public responses.

## 11. Remaining Work and Non-Goals

Persisted pHash calculation, lazy historical backfill, owner-scoped known-original candidate retrieval, Hamming-distance ranking, automatic plausible-candidate selection, ORB/homography alignment, overlap masking, deterministic visual difference, changed-region mask generation, changed-area ratio, and public comparison orchestration are implemented.

The UFD global detector, TruFor localizer adapter, and owner-authenticated public AI-analysis endpoint are implemented. The development repository does not contain model checkpoints, the pinned TruFor checkout, or a real calibration artifact, so the default runtime remains `MODEL_NOT_CONFIGURED`.

Still pending are model provisioning, representative threshold/calibration datasets, evidence fusion/final forensic decisions, C2PA parsing, OCR, frontend forensic-analysis UI, persisted analysis artifacts, and SSIM.

## 12. Implemented UniversalFakeDetect Global Signal

`UniversalFakeDetector` reproduces the released UFD inference architecture: a locally loaded OpenAI CLIP ViT-L/14 image encoder produces 768-dimensional image features, and the trusted released linear classifier produces one logit. Inference converts the image to RGB, deterministically resizes its shorter edge to 256 pixels with bilinear interpolation, center-crops to 224×224, converts it to a tensor, and uses the exact CLIP mean `(0.48145466, 0.4578275, 0.40821073)` and standard deviation `(0.26862954, 0.26130258, 0.27577711)`. There is no random inference augmentation or ImageNet normalization.

The raw score is `sigmoid(rawLogit)`. It is called `rawSyntheticScore`, never confidence. A configurable threshold defaults to `0.5`; scores at or above it are `SYNTHETIC_LEANING`, while lower scores are `REAL_LEANING`. These signals must not be translated into `REAL`, `FAKE`, approval, rejection, or proof.

Both checkpoints are manually provisioned trusted assets. The adapter validates regular files, optional expected SHA-256 values, CLIP ViT-L/14 dimensions, classifier dimensions, and scalar classifier output. The classifier is loaded with PyTorch weights-only loading. Missing files report `MODEL_NOT_CONFIGURED`; invalid files/checksums report `MODEL_INVALID`; unavailable forced CUDA reports `DEVICE_UNAVAILABLE`; and safe request failures report `PROCESSING_FAILED`. The service and health route remain available in every case, and no request triggers a download.

Optional Platt calibration uses `sigmoid(a * rawLogit + b)` only when a schema-valid artifact matches the model name, version, classifier checksum, and optional CLIP checksum. Without one, the calibrated probability is null and status is `NOT_CALIBRATED`; a bad or mismatched artifact produces `CALIBRATION_INVALID`. AuthVault does not fabricate or ship calibration parameters.

`POST /api/v1/assets/{assetId}/analyze-ai` is authenticated, owner-scoped by public UUID, and supports `IMAGE` assets only. Spring streams controlled stored bytes through the internal client and returns separate safe UFD and TruFor sections plus `analyzedAt`. It sends no JWT, user information, database ID, filename metadata, filesystem path, or storage key and does not persist analysis in this phase.

UFD is a global synthetic-image detector. It is not sufficient for partial AI edits, inpainting localization, object-replacement localization, proving authenticity, or reliably detecting every future generator. False positives and false negatives are expected under distribution shift, post-processing, compression, and generator evolution. A future AuthVault forensic report must keep SHA-256, pHash, known-original comparison, UFD, learned localization, and provenance as independent evidence before any carefully calibrated fusion.

Completion state:

- Code implemented: UFD adapter, model loading/versioning, raw score and threshold signal, optional calibration layer/tool, health metadata, and Spring `analyze-ai` endpoint.
- Model not provisioned: CLIP ViT-L/14 checkpoint, UFD classifier checkpoint, and representative calibration artifact.
- Pending: evidence fusion, frontend forensic report, OCR, and C2PA.

## 13. Implemented TruFor General Manipulation Localizer

TruFor is integrated as `GENERAL_MANIPULATION_LOCALIZER`, independently of UFD and the known-original comparator. It produces a raw whole-image `manipulationScore`, pixel-level anomaly map, and pixel-level reliability map. A positive or elevated signal can be associated with multiple manipulation families, including deep-learning-based edits, but it does not establish that AI caused an edit and does not prove falsity, authorship, or intent.

The adapter is pinned to official upstream revision `ae54475df6f41a491d7615100feb19263dec13f7`, with model identity `TRUFOR / TRUFOR_CVPR2023_RELEASED_V1`. Neither source nor weights are committed or downloaded. The administrator must provide the pinned minimal upstream inference runtime and released checkpoint. The official source license is restricted to informational and nonprofit use and expressly excludes unauthorized industrial/profit-oriented use; commercial use requires license review, permission, or model replacement. Full provenance is recorded in `docs/THIRD_PARTY_MODELS.md`.

The pinned upstream environment uses Python 3.7, PyTorch 1.11, torchvision 0.12, CUDA 11.3, and `timm` 0.5.4, which differs materially from AuthVault's primary AI-service environment. AuthVault therefore isolates all imports behind a lazy runtime adapter and makes `timm` an optional dependency rather than downgrading the service. A target deployment must run the optional real-model smoke test; incompatible deployments should move this same adapter contract behind a separately licensed model worker/container in a later infrastructure task.

Inputs retain the existing JPEG/PNG signature, decoding, decompression-bomb, pixel-count, EXIF-orientation, and RGB protections. The official runtime receives original dimensions and upstream-compatible RGB scaling without arbitrary sharpening, denoising, or enhancement. Returned maps are required to be two-dimensional, finite, and non-empty, are clamped to `[0,1]`, and are deterministically bilinear-mapped to original-image coordinates when necessary.

`manipulationScore` is a raw detector score, not confidence and not a probability of AI involvement. The configurable default `0.5` threshold produces only `LOW_MANIPULATION_SIGNAL` or `ELEVATED_MANIPULATION_SIGNAL`. No asset status, final verdict, upload decision, or automatic rejection is changed by TruFor.

`suspiciousAreaRatio` is the fraction of original-image pixels whose anomaly value meets the configured anomaly threshold. `reliableSuspiciousAreaRatio` is the selected anomalous fraction among pixels meeting the minimum reliability threshold; it is null when no sufficiently reliable pixels exist. The binary PNG mask selects pixels meeting both thresholds and is named a manipulation-suspicion mask, never an AI-generated-region mask.

Anomaly and reliability maps remain separate. AuthVault does not merge them into a fabricated confidence value. Three grayscale visualization PNGs are returned temporarily as Base64 and are not persisted. Ratios are calculated before visualization downscaling. Visualizations preserve aspect ratio and have a configurable maximum edge (default 1024 pixels) and combined encoded-byte limit (default 8 MiB).

TruFor loads once, uses evaluation/inference mode, and has a dedicated bounded executor/concurrency guard and configurable timeout. Controlled statuses are `MODEL_NOT_CONFIGURED`, `MODEL_INVALID`, `DEVICE_UNAVAILABLE`, `PROCESSING_FAILED`, and `PROCESSING_TIMEOUT`. Missing or invalid TruFor assets never prevent FastAPI health, UFD, comparison, upload, integrity, or asset-management behavior.

The existing public `POST /api/v1/assets/{assetId}/analyze-ai` response now contains independent `aiGenerationAnalysis` and `manipulationAnalysis` sections plus one overall `analyzedAt`. It remains authenticated, owner-scoped, IMAGE-only, streamed through Spring, and free of database IDs, paths, user data, JWTs, internal URLs, and Python errors. No forensic result is persisted yet.

Two localization sources now coexist deliberately:

- Known-original comparison: deterministic visual difference after pair alignment.
- TruFor: learned single-image general-manipulation anomaly evidence.

Their masks are not compared, overlapped, or fused in this phase.

Completion state:

- Code implemented: pinned TruFor runtime adapter, lifecycle/device/checksum validation, raw score/signal, map normalization, area calculations, bounded PNGs, timeout/concurrency handling, health metadata, and nested Spring response mapping.
- Model not provisioned: pinned upstream runtime checkout and released `trufor.pth.tar` checkpoint.
- Pending: representative threshold evaluation, evidence fusion, persistence, frontend report, OCR, and C2PA.
