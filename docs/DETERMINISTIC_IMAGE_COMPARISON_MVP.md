# Deterministic Image Comparison MVP

## Scope

VaultChain compares a controlled target image with a known or
Fabric-confirmed original. The comparison is deterministic image processing,
not learned-model inference and not a final authenticity decision.

This contract complements `UPLOAD_INTEGRITY_MVP.md` and
`VERIFY_ORIGINALITY_MVP.md`. SHA-256 integrity, pHash retrieval, deterministic
comparison, and Fabric provenance remain separate evidence.

## Service boundary

Spring Boot remains the public API and owns authentication, authorization,
asset ownership, persistence, and storage access. It sends only controlled
image bytes to the internal FastAPI service. The FastAPI service does not
access the database, validate public JWTs, or accept browser traffic.

The service lives in `comparison-service/`. Spring binds its connection
settings under `authvault.image-comparison`, and Python comparison settings use
the `AUTHVAULT_IMAGE_COMPARISON_*` environment namespace. Legacy environment
aliases remain temporarily available for one cleanup cycle.

## Internal API

`POST /v1/compare/images` accepts multipart JPEG or PNG fields named
`reference` and `target`. It normalizes EXIF orientation, attempts ORB feature
matching and RANSAC homography alignment, and uses an explicit resize fallback
when reliable alignment is unavailable.

The response contains:

- alignment status and diagnostics;
- changed-area ratio, mean absolute difference, and structural similarity;
- an optional bounded Base64 PNG change mask;
- a technical comparison status.

Technical statuses are `COMPLETED`, `NO_VALID_OVERLAP`, and
`PROCESSING_FAILED`. Alignment statuses are `ALIGNED`, `FALLBACK_RESIZE`, and
`ALIGNMENT_FAILED`.

`GET /health` reports only deterministic comparison-service availability.

## Interpretation

A visual difference means the two images differ after the documented
alignment and filtering stages. It does not prove cause, authorship,
authenticity, infringement, fraud, or intent. No model probability or learned
confidence is produced.

The service does not persist inputs or masks, inspect filenames for identity,
or expose filesystem paths, internal exceptions, user information, database
IDs, or JWTs.
