import math
import os
from pathlib import Path

import pytest
from PIL import Image

from app.core.config import Settings
from app.detectors.universal_fake_detector import UniversalFakeDetector


def test_real_ufd_model_smoke_when_explicitly_provisioned() -> None:
    clip_path = _asset_path("AUTHVAULT_UFD_SMOKE_CLIP_CHECKPOINT")
    classifier_path = _asset_path("AUTHVAULT_UFD_SMOKE_CLASSIFIER_CHECKPOINT")
    if clip_path is None or classifier_path is None:
        pytest.skip("real UFD model assets were not explicitly provided")

    detector = UniversalFakeDetector(
        Settings(
            ufd_enabled=True,
            ufd_clip_checkpoint=clip_path,
            ufd_classifier_checkpoint=classifier_path,
            ufd_device=os.getenv("AUTHVAULT_UFD_SMOKE_DEVICE", "cpu"),
        )
    )
    assert detector.ready
    model_identity = id(detector._clip_model)
    image = Image.new("RGB", (224, 224), "gray")
    first = detector.analyze(image)
    second = detector.analyze(image)

    assert id(detector._clip_model) == model_identity
    assert first.performed and second.performed
    assert first.raw_logit is not None and math.isfinite(first.raw_logit)
    assert first.raw_synthetic_score is not None
    assert 0.0 <= first.raw_synthetic_score <= 1.0


def _asset_path(name: str) -> Path | None:
    value = os.getenv(name)
    if value is None or not value.strip():
        return None
    path = Path(value.strip())
    return path if path.is_file() else None
