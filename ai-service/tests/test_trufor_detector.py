import base64
import hashlib
import math
import time
from io import BytesIO
from pathlib import Path

import numpy as np
import pytest
import torch
from PIL import Image

from app.core.config import Settings
from app.detectors.trufor_detector import (
    PINNED_UPSTREAM_REVISION,
    RawTruForOutput,
    TruForDetector,
    _require_pinned_checkout,
    process_trufor_output,
)
from app.models.schemas import AnalysisStatus, ManipulationSignal


class FakeRuntime:
    def __init__(self, output: RawTruForOutput, *, delay: float = 0.0) -> None:
        self.output = output
        self.delay = delay
        self.inference_mode_observed = False
        self.calls = 0

    def infer(self, image: Image.Image) -> RawTruForOutput:
        self.calls += 1
        self.inference_mode_observed = not torch.is_grad_enabled()
        if self.delay:
            time.sleep(self.delay)
        return self.output


def test_disabled_detector_reports_model_not_configured() -> None:
    detector = TruForDetector(Settings(), runtime_loader=_unexpected_runtime_loader)

    result = detector.analyze(Image.new("RGB", (2, 2)))

    assert detector.configured is False
    assert result.performed is False
    assert result.status == AnalysisStatus.MODEL_NOT_CONFIGURED


def test_missing_model_or_runtime_reports_model_not_configured(tmp_path: Path) -> None:
    detector = TruForDetector(
        enabled_settings(tmp_path / "missing.pth", tmp_path / "missing-runtime"),
        runtime_loader=_unexpected_runtime_loader,
    )

    assert detector.configured is True
    assert detector.status == AnalysisStatus.MODEL_NOT_CONFIGURED


def test_invalid_model_runtime_and_checksum_are_controlled(tmp_path: Path) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    invalid_runtime = TruForDetector(
        enabled_settings(model_path, runtime_path),
        runtime_loader=lambda *_args: (_ for _ in ()).throw(ValueError("invalid")),
    )
    assert invalid_runtime.status == AnalysisStatus.MODEL_INVALID

    checksum_mismatch = TruForDetector(
        enabled_settings(
            model_path,
            runtime_path,
            trufor_model_sha256="0" * 64,
        ),
        runtime_loader=_unexpected_runtime_loader,
    )
    assert checksum_mismatch.status == AnalysisStatus.MODEL_INVALID

    wrong_revision = TruForDetector(
        enabled_settings(
            model_path,
            runtime_path,
            trufor_runtime_revision="1" * 40,
        ),
        runtime_loader=_unexpected_runtime_loader,
    )
    assert wrong_revision.status == AnalysisStatus.MODEL_INVALID


def test_forced_cuda_unavailable_is_controlled(tmp_path: Path) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    detector = TruForDetector(
        enabled_settings(
            model_path,
            runtime_path,
            trufor_device="cuda",
        ),
        runtime_loader=_unexpected_runtime_loader,
        cuda_available=lambda: False,
    )

    assert detector.status == AnalysisStatus.DEVICE_UNAVAILABLE


@pytest.mark.parametrize(
    ("score", "signal"),
    [
        (0.49, ManipulationSignal.LOW_MANIPULATION_SIGNAL),
        (0.5, ManipulationSignal.ELEVATED_MANIPULATION_SIGNAL),
        (0.9, ManipulationSignal.ELEVATED_MANIPULATION_SIGNAL),
    ],
)
def test_successful_inference_maps_score_signal_and_uses_inference_mode(
    tmp_path: Path,
    score: float,
    signal: ManipulationSignal,
) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    runtime = FakeRuntime(artificial_output(score))
    detector = TruForDetector(
        enabled_settings(model_path, runtime_path),
        runtime_loader=lambda *_args: runtime,
    )

    result = detector.analyze(Image.new("RGB", (2, 2)))

    assert result.performed is True
    assert result.status == AnalysisStatus.COMPLETED
    assert result.model_name == "TRUFOR"
    assert result.model_version == "TRUFOR_CVPR2023_RELEASED_V1"
    assert result.manipulation_score == score
    assert result.model_signal == signal
    assert result.localization_available is True
    assert result.suspicious_area_ratio == pytest.approx(0.5)
    assert result.reliable_suspicious_area_ratio == pytest.approx(1 / 3)
    assert runtime.inference_mode_observed is True
    assert decode_png(result.anomaly_map_png_base64).size == (2, 2)
    assert decode_png(result.reliability_map_png_base64).size == (2, 2)
    assert int(np.asarray(decode_png(result.suspicious_mask_png_base64))[0, 0]) == 0


def test_synthetic_map_threshold_math_and_binary_mask() -> None:
    output = process_trufor_output(
        artificial_output(0.7),
        original_size=(2, 2),
        anomaly_threshold=0.5,
        minimum_reliability=0.5,
        maximum_map_dimension=100,
        maximum_encoded_bytes=10_000,
    )

    assert output.suspicious_area_ratio == pytest.approx(0.5)
    assert output.reliable_suspicious_area_ratio == pytest.approx(1 / 3)
    with decode_png(output.suspicious_mask_png_base64) as mask:
        assert np.asarray(mask).reshape(-1).tolist() == [0, 255, 0, 0]


def test_no_reliable_pixels_returns_null_ratio() -> None:
    output = process_trufor_output(
        RawTruForOutput(
            manipulation_score=0.2,
            anomaly_map=np.array([[0.9, 0.8]], dtype=np.float32),
            reliability_map=np.array([[0.1, 0.2]], dtype=np.float32),
        ),
        original_size=(2, 1),
        anomaly_threshold=0.5,
        minimum_reliability=0.9,
        maximum_map_dimension=100,
        maximum_encoded_bytes=10_000,
    )

    assert output.reliable_suspicious_area_ratio is None
    with decode_png(output.suspicious_mask_png_base64) as mask:
        assert np.asarray(mask).reshape(-1).tolist() == [0, 0]


@pytest.mark.parametrize("original_size", [(7, 3), (3, 7), (5, 5), (7, 5)])
def test_maps_are_mapped_without_stretching_and_visuals_preserve_aspect_ratio(
    original_size: tuple[int, int],
) -> None:
    output = process_trufor_output(
        RawTruForOutput(
            manipulation_score=0.4,
            anomaly_map=np.array([[0.0, 1.0], [0.25, 0.75]], dtype=np.float32),
            reliability_map=np.ones((2, 2), dtype=np.float32),
        ),
        original_size=original_size,
        anomaly_threshold=0.5,
        minimum_reliability=0.5,
        maximum_map_dimension=100,
        maximum_encoded_bytes=100_000,
    )

    assert decode_png(output.anomaly_map_png_base64).size == original_size
    assert decode_png(output.reliability_map_png_base64).size == original_size
    assert decode_png(output.suspicious_mask_png_base64).size == original_size


def test_visual_maps_are_bounded_with_aspect_ratio_preserved() -> None:
    output = process_trufor_output(
        RawTruForOutput(0.4, np.ones((4, 8)), np.ones((4, 8))),
        original_size=(2000, 1000),
        anomaly_threshold=0.5,
        minimum_reliability=0.5,
        maximum_map_dimension=500,
        maximum_encoded_bytes=100_000,
    )

    assert decode_png(output.anomaly_map_png_base64).size == (500, 250)


def test_map_values_are_clamped_and_output_byte_limit_is_enforced() -> None:
    output = process_trufor_output(
        RawTruForOutput(0.4, np.array([[-2.0, 3.0]]), np.array([[2.0, -1.0]])),
        original_size=(2, 1),
        anomaly_threshold=0.5,
        minimum_reliability=0.5,
        maximum_map_dimension=100,
        maximum_encoded_bytes=10_000,
    )
    with decode_png(output.anomaly_map_png_base64) as anomaly:
        assert np.asarray(anomaly).reshape(-1).tolist() == [0, 255]
    with pytest.raises(ValueError, match="safe limit"):
        process_trufor_output(
            artificial_output(0.4),
            original_size=(2, 2),
            anomaly_threshold=0.5,
            minimum_reliability=0.5,
            maximum_map_dimension=100,
            maximum_encoded_bytes=1,
        )


def test_runtime_git_checkout_revision_is_verified(tmp_path: Path) -> None:
    repository = tmp_path / "TruFor"
    runtime = repository / "test_docker" / "src"
    git_directory = repository / ".git"
    runtime.mkdir(parents=True)
    git_directory.mkdir()
    (git_directory / "HEAD").write_text(PINNED_UPSTREAM_REVISION, encoding="ascii")

    _require_pinned_checkout(runtime)

    (git_directory / "HEAD").write_text("0" * 40, encoding="ascii")
    with pytest.raises(ValueError, match="pinned revision"):
        _require_pinned_checkout(runtime)


@pytest.mark.parametrize(
    "bad_output",
    [
        RawTruForOutput(float("nan"), np.zeros((2, 2)), np.zeros((2, 2))),
        RawTruForOutput(0.5, np.array([[float("nan")]]), np.zeros((1, 1))),
        RawTruForOutput(0.5, np.zeros((1, 1)), np.array([[float("inf")]])),
        RawTruForOutput(1.5, np.zeros((1, 1)), np.zeros((1, 1))),
    ],
)
def test_nonfinite_or_invalid_outputs_are_rejected(bad_output: RawTruForOutput) -> None:
    with pytest.raises(ValueError):
        process_trufor_output(
            bad_output,
            original_size=(2, 2),
            anomaly_threshold=0.5,
            minimum_reliability=0.5,
            maximum_map_dimension=100,
            maximum_encoded_bytes=10_000,
        )


def test_processing_failure_returns_no_fabricated_values(tmp_path: Path) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    runtime = FakeRuntime(
        RawTruForOutput(0.5, np.array([[float("nan")]]), np.ones((1, 1)))
    )
    detector = TruForDetector(
        enabled_settings(model_path, runtime_path),
        runtime_loader=lambda *_args: runtime,
    )

    result = detector.analyze(Image.new("RGB", (2, 2)))

    assert result.performed is False
    assert result.status == AnalysisStatus.PROCESSING_FAILED
    assert result.manipulation_score is None
    assert result.anomaly_map_png_base64 is None


def test_processing_timeout_is_controlled(tmp_path: Path) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    runtime = FakeRuntime(artificial_output(0.5), delay=0.05)
    detector = TruForDetector(
        enabled_settings(
            model_path,
            runtime_path,
            trufor_inference_timeout_seconds=0.01,
        ),
        runtime_loader=lambda *_args: runtime,
    )

    result = detector.analyze(Image.new("RGB", (2, 2)))

    assert result.performed is False
    assert result.status == AnalysisStatus.PROCESSING_TIMEOUT


def test_checkpoint_sha256_is_streamed_and_retained(tmp_path: Path) -> None:
    model_path, runtime_path = provisioned_paths(tmp_path)
    expected = hashlib.sha256(model_path.read_bytes()).hexdigest()
    detector = TruForDetector(
        enabled_settings(
            model_path,
            runtime_path,
            trufor_model_sha256=expected,
        ),
        runtime_loader=lambda *_args: FakeRuntime(artificial_output(0.5)),
    )

    assert detector.ready
    assert detector.checkpoint_sha256 == expected


def artificial_output(score: float) -> RawTruForOutput:
    return RawTruForOutput(
        manipulation_score=score,
        anomaly_map=np.array([[0.1, 0.9], [0.8, 0.2]], dtype=np.float32),
        reliability_map=np.array([[0.9, 0.9], [0.2, 0.8]], dtype=np.float32),
    )


def provisioned_paths(tmp_path: Path) -> tuple[Path, Path]:
    model_path = tmp_path / "trufor.pth.tar"
    runtime_path = tmp_path / "runtime"
    model_path.write_bytes(b"trusted trufor test checkpoint")
    runtime_path.mkdir()
    return model_path, runtime_path


def enabled_settings(model_path: Path, runtime_path: Path, **overrides: object) -> Settings:
    values = {
        "trufor_enabled": True,
        "trufor_model_path": model_path,
        "trufor_runtime_path": runtime_path,
        "trufor_runtime_revision": PINNED_UPSTREAM_REVISION,
    }
    values.update(overrides)
    return Settings(**values)


def decode_png(value: str | None) -> Image.Image:
    assert value is not None
    image = Image.open(BytesIO(base64.b64decode(value)))
    image.load()
    return image


def _unexpected_runtime_loader(_runtime: Path, _model: Path, _device: str):
    raise AssertionError("TruFor runtime loader must not run")
