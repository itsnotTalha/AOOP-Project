import base64
import math
import os
from io import BytesIO
from pathlib import Path

import pytest
from PIL import Image

from app.core.config import Settings
from app.detectors.trufor_detector import PINNED_UPSTREAM_REVISION, TruForDetector


def test_real_trufor_model_smoke_when_explicitly_enabled() -> None:
    if os.getenv("AUTHVAULT_TRUFOR_SMOKE_ENABLED", "false").lower() != "true":
        pytest.skip("real TruFor smoke test was not explicitly enabled")
    model_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_MODEL_PATH", file=True)
    runtime_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_RUNTIME_PATH", file=False)
    image_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_IMAGE_PATH", file=True)
    detector = TruForDetector(
        Settings(
            trufor_enabled=True,
            trufor_model_path=model_path,
            trufor_runtime_path=runtime_path,
            trufor_runtime_revision=PINNED_UPSTREAM_REVISION,
            trufor_device=os.getenv("AUTHVAULT_TRUFOR_SMOKE_DEVICE", "cpu"),
            trufor_inference_timeout_seconds=300,
        )
    )
    assert detector.ready
    runtime_identity = id(detector._runtime)
    with Image.open(image_path) as source:
        image = source.convert("RGB")
        image.load()
    first = detector.analyze(image)
    second = detector.analyze(image)

    assert id(detector._runtime) == runtime_identity
    assert first.performed and second.performed
    assert first.manipulation_score is not None
    assert math.isfinite(first.manipulation_score)
    assert 0.0 <= first.manipulation_score <= 1.0
    assert first.anomaly_map_png_base64 is not None
    assert first.reliability_map_png_base64 is not None
    assert first.suspicious_mask_png_base64 is not None
    with Image.open(BytesIO(base64.b64decode(first.anomaly_map_png_base64))) as map_image:
        scale = min(1.0, 1024 / max(image.size))
        assert map_image.size == (
            max(1, round(image.width * scale)),
            max(1, round(image.height * scale)),
        )
    image.close()


def test_real_trufor_local_edit_smoke_when_explicitly_enabled() -> None:
    if os.getenv("AUTHVAULT_TRUFOR_SMOKE_ENABLED", "false").lower() != "true":
        pytest.skip("real TruFor smoke test was not explicitly enabled")
    model_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_MODEL_PATH", file=True)
    runtime_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_RUNTIME_PATH", file=False)
    edited_path = _required_path("AUTHVAULT_TRUFOR_SMOKE_EDITED_IMAGE_PATH", file=True)
    detector = TruForDetector(
        Settings(
            trufor_enabled=True,
            trufor_model_path=model_path,
            trufor_runtime_path=runtime_path,
            trufor_runtime_revision=PINNED_UPSTREAM_REVISION,
            trufor_device=os.getenv("AUTHVAULT_TRUFOR_SMOKE_DEVICE", "cpu"),
            trufor_inference_timeout_seconds=300,
        )
    )
    assert detector.ready
    with Image.open(edited_path) as source:
        edited = source.convert("RGB")
        edited.load()
    result = detector.analyze(edited)
    edited.close()

    assert result.performed
    assert result.manipulation_score is not None
    assert math.isfinite(result.manipulation_score)
    assert result.anomaly_map_png_base64
    assert result.reliability_map_png_base64
    assert result.suspicious_mask_png_base64
    assert result.suspicious_area_ratio is not None


def _required_path(name: str, *, file: bool) -> Path:
    value = os.getenv(name)
    if value is None:
        pytest.skip(f"{name} was not provided")
    path = Path(value)
    if (file and not path.is_file()) or (not file and not path.is_dir()):
        pytest.skip(f"{name} is unavailable")
    return path
