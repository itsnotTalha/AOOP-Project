# AuthVault AI Service

## Purpose

Internal FastAPI boundary for replaceable image-forensics detectors called only by Spring Boot.

## Prerequisites

Python 3.11 or newer and Git (used to install the exactly pinned OpenAI CLIP dependency). Model weights are not included.

## Install

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -e ".[test]"
```

## Development

```bash
uvicorn app.main:app --reload --port 8001
```

## Test

```bash
pytest
```

## Current Status

The UniversalFakeDetect (UFD) global synthetic-image adapter and TruFor general-manipulation localizer adapter are implemented. Model assets and the restricted TruFor runtime are not committed or provisioned by default, so the service starts safely and reports `MODEL_NOT_CONFIGURED` for disabled/unprovisioned detectors. No code or weights are downloaded during startup, health checks, or analysis requests.

Known-original comparison is available internally at `POST /v1/compare/images`. It accepts controlled `reference` and `target` JPEG/PNG parts, normalizes EXIF orientation, attempts ORB/RANSAC homography alignment with an explicit resize fallback, and returns deterministic difference metrics plus a Base64 PNG change mask. It detects visual changes only and does not classify an image as real, fake, or AI-edited.

Comparison limits and thresholds use the `AUTHVAULT_AI_COMPARISON_*` variables documented in `.env.example`. Defaults are conservative starting points and require calibration against representative images.

## UniversalFakeDetect Model Provisioning

UFD requires two trusted third-party files:

1. The OpenAI CLIP ViT-L/14 checkpoint.
2. The released UniversalFakeDetect linear-classifier `fc_weights.pth` checkpoint.

Obtain these manually by following the official [OpenAI CLIP](https://github.com/openai/CLIP) and [UniversalFakeDetect](https://github.com/WisconsinAIVision/UniversalFakeDetect) repositories. AuthVault intentionally does not guess, mirror, or automatically invoke checkpoint download URLs. Review the upstream licenses and checkpoint provenance before use, calculate SHA-256 checksums, and place the files outside version control, for example:

```text
model-assets/ufd/clip-vit-l14.pt
model-assets/ufd/fc_weights.pth
model-assets/ufd/calibration.json  # optional; must be fitted locally
```

Configure the service explicitly:

```bash
export AUTHVAULT_UFD_ENABLED=true
export AUTHVAULT_UFD_CLIP_CHECKPOINT=model-assets/ufd/clip-vit-l14.pt
export AUTHVAULT_UFD_CLASSIFIER_CHECKPOINT=model-assets/ufd/fc_weights.pth
export AUTHVAULT_UFD_DEVICE=auto
export AUTHVAULT_UFD_CLIP_SHA256=<lowercase-sha256>
export AUTHVAULT_UFD_CLASSIFIER_SHA256=<lowercase-sha256>
export AUTHVAULT_UFD_DECISION_THRESHOLD=0.5
export AUTHVAULT_UFD_MAX_CONCURRENT_INFERENCE=1
```

`AUTHVAULT_UFD_DEVICE` accepts `auto`, `cpu`, or `cuda`. Forced CUDA reports `DEVICE_UNAVAILABLE` when CUDA is absent rather than stopping the API. ViT-L/14 can be slow on CPU; configure Spring's existing `AUTHVAULT_AI_READ_TIMEOUT` to a measured deployment-specific duration instead of using an unbounded timeout.

The OpenAI CLIP code dependency is pinned to commit `d05afc436d78f1c48dc0dbf8e5980a9d471f35f6`. The configured checkpoint is passed to `clip.load` as a local path, never as a downloadable model name.

## Optional Offline Calibration

Raw UFD output is reported as `rawSyntheticScore`, not as confidence or a calibrated probability. To fit an optional Platt-scaling artifact from a representative labeled validation set:

```bash
python -m app.tools.calibrate_ufd \
  --real-dir /path/to/real \
  --synthetic-dir /path/to/synthetic \
  --output model-assets/ufd/calibration.json \
  --dataset-id my-validation-set-v1
```

The tool requires both classes, processes paths deterministically, enforces the configured minimum sample count, and refuses to overwrite an artifact unless `--overwrite` is given. It prints measured Brier scores before and after calibration. Configure the resulting file with `AUTHVAULT_UFD_CALIBRATION_PATH`. Missing calibration produces `NOT_CALIBRATED`; invalid or checkpoint-mismatched calibration produces `CALIBRATION_INVALID` while raw inference remains available.

## Health Semantics

`modelsReady` is true when at least one learned detector is configured and every enabled detector is ready. Disabled UFD or TruFor detectors do not make an enabled ready model unhealthy. Per-model `configured` and `ready` metadata is returned under `models`; checkpoint paths, hashes, and device details are not exposed.

## TruFor Local Manipulation Runtime

The TruFor adapter is implemented as the independent `ManipulationDetector` slot. TruFor is a general manipulation localizer, not proof of AI editing. Its upstream license restricts use, reproduction, and modification to informational and nonprofit purposes. Commercial deployment requires a separate legal/license review or a replacement model. See [the central third-party notice](../docs/THIRD_PARTY_MODELS.md).

AuthVault pins the upstream source revision to `ae54475df6f41a491d7615100feb19263dec13f7` and does not vendor or download the source or checkpoint. Manually provision:

1. A trusted Git checkout of the official repository at that exact revision, retaining its `.git` metadata so AuthVault can verify `HEAD`; configure `AUTHVAULT_TRUFOR_RUNTIME_PATH` to its `test_docker/src` directory.
2. The released `trufor.pth.tar` checkpoint; configure `AUTHVAULT_TRUFOR_MODEL_PATH` and preferably its independently calculated SHA-256 in `AUTHVAULT_TRUFOR_MODEL_SHA256`.
3. The optional inference dependency with `python -m pip install -e ".[trufor]"` only in a TruFor-enabled environment.

The official released-weights archive is documented upstream with MD5 `7bee48f3476c75616c3c5721ab256ff8`; this is provenance information, not AuthVault's checkpoint SHA-256. Calculate SHA-256 after extracting the actual configured checkpoint.

```bash
export AUTHVAULT_TRUFOR_ENABLED=true
export AUTHVAULT_TRUFOR_RUNTIME_PATH=/trusted/TruFor/test_docker/src
export AUTHVAULT_TRUFOR_RUNTIME_REVISION=ae54475df6f41a491d7615100feb19263dec13f7
export AUTHVAULT_TRUFOR_MODEL_PATH=model-assets/trufor/trufor.pth.tar
export AUTHVAULT_TRUFOR_MODEL_SHA256=<lowercase-sha256>
export AUTHVAULT_TRUFOR_DEVICE=auto
export AUTHVAULT_TRUFOR_MAX_CONCURRENT_INFERENCE=1
export AUTHVAULT_TRUFOR_INFERENCE_TIMEOUT_SECONDS=60
export AUTHVAULT_TRUFOR_SCORE_THRESHOLD=0.5
export AUTHVAULT_TRUFOR_ANOMALY_THRESHOLD=0.5
export AUTHVAULT_TRUFOR_MIN_RELIABILITY=0.5
export AUTHVAULT_TRUFOR_MAX_MAP_DIMENSION=1024
export AUTHVAULT_TRUFOR_MAX_MAP_BYTES=8388608
```

The pinned official runtime was published for Python 3.7, PyTorch 1.11, torchvision 0.12, CUDA 11.3, and `timm` 0.5.4. AuthVault does not downgrade its primary environment. The adapter imports the manually provisioned minimal inference runtime only when enabled, while `timm` is isolated in the optional `trufor` dependency extra. Validate real-model compatibility in the target deployment. If the pinned runtime is incompatible, deploy it as a separately licensed model worker/container in a later infrastructure task rather than changing UFD, FastAPI, or comparison dependencies.

TruFor receives the already validated, EXIF-normalized RGB image at its original dimensions and follows upstream input scaling. Its original anomaly and reliability outputs are normalized independently to finite `[0,1]` maps and mapped back to original coordinates. Ratios use the original-size maps. Returned grayscale PNG visualizations preserve aspect ratio and are downscaled only when their longest edge exceeds `AUTHVAULT_TRUFOR_MAX_MAP_DIMENSION`; their combined encoded PNG payload is bounded by `AUTHVAULT_TRUFOR_MAX_MAP_BYTES`.

The Spring analysis call has a separate measured timeout, `AUTHVAULT_AI_ANALYSIS_READ_TIMEOUT` (default `60s`), while comparison keeps `AUTHVAULT_AI_READ_TIMEOUT` (default `10s`). CPU localization can be substantially slower; tune the analysis timeout from deployment measurements instead of making it unbounded.
