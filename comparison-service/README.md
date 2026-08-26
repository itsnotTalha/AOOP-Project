# AuthVault Image Comparison Service

## Purpose

This FastAPI service provides deterministic, stateless image comparison for
Spring Boot.

Spring remains the public API and owns authentication, authorization, asset
access, and storage. Browsers do not call this service directly.

## Prerequisites

Python 3.11 or newer.

## Install

```bash
python -m venv .venv
. .venv/bin/activate
python -m pip install -e ".[test]"
```

## Development

```bash
uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

## Test

```bash
pytest
```

## Internal API

- `GET /health` reports service availability.
- `POST /v1/compare/images` accepts controlled `reference` and `target`
  JPEG/PNG multipart fields.

The comparison pipeline normalizes EXIF orientation, attempts ORB/RANSAC
homography alignment with an explicit resize fallback, and returns
deterministic difference metrics plus an optional Base64 PNG change mask. It
does not classify an image as authentic, fake, AI-generated, or AI-edited.

Limits and thresholds use the `AUTHVAULT_IMAGE_COMPARISON_*` environment
namespace documented in `.env.example`. The service temporarily accepts the
former comparison variable names as fallback aliases for one cleanup cycle.
