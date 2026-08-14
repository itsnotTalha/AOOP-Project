import hashlib
import json
import math
from pathlib import Path
from types import SimpleNamespace

import pytest
import torch
from PIL import Image

from app.calibration.ufd import UfdCalibrationArtifact
from app.core.config import Settings
from app.detectors.universal_fake_detector import (
    CLIP_FEATURE_DIMENSION,
    CLIP_INPUT_SIZE,
    UniversalFakeDetector,
    build_ufd_preprocess,
)
from app.models.schemas import AnalysisStatus, CalibrationStatus, ModelSignal


class FakeClipModel:
    def __init__(self, feature_value: float = 0.0) -> None:
        self.visual = SimpleNamespace(
            output_dim=CLIP_FEATURE_DIMENSION,
            input_resolution=CLIP_INPUT_SIZE,
        )
        self.feature_value = feature_value
        self.eval_called = False
        self.inference_mode_observed = False

    def eval(self) -> "FakeClipModel":
        self.eval_called = True
        return self

    def encode_image(self, input_tensor: torch.Tensor) -> torch.Tensor:
        self.inference_mode_observed = not torch.is_grad_enabled()
        return torch.full(
            (input_tensor.shape[0], CLIP_FEATURE_DIMENSION),
            self.feature_value,
            device=input_tensor.device,
        )


def test_disabled_detector_reports_model_not_configured() -> None:
    detector = UniversalFakeDetector(Settings(), clip_loader=_unexpected_clip_loader)

    result = detector.analyze(sample_image())

    assert result.performed is False
    assert result.status == AnalysisStatus.MODEL_NOT_CONFIGURED
    assert detector.configured is False


def test_missing_checkpoint_reports_model_not_configured(tmp_path: Path) -> None:
    settings = enabled_settings(
        tmp_path / "missing-clip.pt", tmp_path / "missing-classifier.pth"
    )

    detector = UniversalFakeDetector(settings, clip_loader=_unexpected_clip_loader)

    assert detector.status == AnalysisStatus.MODEL_NOT_CONFIGURED
    assert detector.configured is True
    assert detector.ready is False


def test_invalid_classifier_checkpoint_reports_model_invalid(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    detector = UniversalFakeDetector(
        enabled_settings(clip_path, classifier_path),
        clip_loader=lambda _path, _device: FakeClipModel(),
        classifier_loader=lambda _path: {"weight": torch.zeros((2, 768))},
    )

    assert detector.status == AnalysisStatus.MODEL_INVALID


def test_invalid_clip_checkpoint_reports_model_invalid(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)

    def invalid_clip(_path: Path, _device: str) -> FakeClipModel:
        raise ValueError("invalid checkpoint")

    detector = UniversalFakeDetector(
        enabled_settings(clip_path, classifier_path),
        clip_loader=invalid_clip,
        classifier_loader=lambda _path: classifier_state(0.0),
    )

    assert detector.status == AnalysisStatus.MODEL_INVALID


def test_forced_cuda_unavailable_is_controlled(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    settings = enabled_settings(clip_path, classifier_path, ufd_device="cuda")

    detector = UniversalFakeDetector(
        settings,
        clip_loader=_unexpected_clip_loader,
        cuda_available=lambda: False,
    )

    assert detector.status == AnalysisStatus.DEVICE_UNAVAILABLE
    assert detector.analyze(sample_image()).performed is False


@pytest.mark.parametrize(
    ("raw_logit", "expected_signal"),
    [
        (-2.0, ModelSignal.REAL_LEANING),
        (0.0, ModelSignal.SYNTHETIC_LEANING),
        (2.0, ModelSignal.SYNTHETIC_LEANING),
    ],
)
def test_cpu_inference_sigmoid_threshold_eval_and_inference_mode(
    tmp_path: Path,
    raw_logit: float,
    expected_signal: ModelSignal,
) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    clip_model = FakeClipModel()
    detector = UniversalFakeDetector(
        enabled_settings(clip_path, classifier_path, ufd_device="cpu"),
        clip_loader=lambda _path, device: clip_model if device == "cpu" else None,
        classifier_loader=lambda _path: classifier_state(raw_logit),
    )

    result = detector.analyze(sample_image())

    assert detector.ready is True
    assert clip_model.eval_called is True
    assert clip_model.inference_mode_observed is True
    assert result.performed is True
    assert result.status == AnalysisStatus.COMPLETED
    assert result.raw_logit == pytest.approx(raw_logit)
    assert result.raw_synthetic_score == pytest.approx(1 / (1 + math.exp(-raw_logit)))
    assert result.model_signal == expected_signal
    assert result.calibration_status == CalibrationStatus.NOT_CALIBRATED
    assert result.calibrated_synthetic_probability is None


def test_valid_calibration_is_applied_to_raw_logit(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    classifier_hash = hashlib.sha256(classifier_path.read_bytes()).hexdigest()
    clip_hash = hashlib.sha256(clip_path.read_bytes()).hexdigest()
    calibration_path = tmp_path / "calibration.json"
    artifact = calibration_artifact(classifier_hash, clip_hash, a=2.0, b=-1.0)
    calibration_path.write_text(artifact.model_dump_json(by_alias=True), encoding="utf-8")
    detector = UniversalFakeDetector(
        enabled_settings(
            clip_path,
            classifier_path,
            ufd_calibration_path=calibration_path,
        ),
        clip_loader=lambda _path, _device: FakeClipModel(),
        classifier_loader=lambda _path: classifier_state(1.0),
    )

    result = detector.analyze(sample_image())

    assert result.calibration_status == CalibrationStatus.CALIBRATED
    assert result.calibrated_synthetic_probability == pytest.approx(
        1 / (1 + math.exp(-1.0))
    )


def test_calibration_checkpoint_mismatch_is_rejected(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    clip_hash = hashlib.sha256(clip_path.read_bytes()).hexdigest()
    calibration_path = tmp_path / "calibration.json"
    artifact = calibration_artifact("0" * 64, clip_hash)
    calibration_path.write_text(artifact.model_dump_json(by_alias=True), encoding="utf-8")
    detector = UniversalFakeDetector(
        enabled_settings(
            clip_path,
            classifier_path,
            ufd_calibration_path=calibration_path,
        ),
        clip_loader=lambda _path, _device: FakeClipModel(),
        classifier_loader=lambda _path: classifier_state(0.25),
    )

    result = detector.analyze(sample_image())

    assert result.performed is True
    assert result.calibration_status == CalibrationStatus.CALIBRATION_INVALID
    assert result.calibrated_synthetic_probability is None


def test_calibration_model_version_mismatch_is_rejected(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    classifier_hash = hashlib.sha256(classifier_path.read_bytes()).hexdigest()
    clip_hash = hashlib.sha256(clip_path.read_bytes()).hexdigest()
    calibration_path = tmp_path / "calibration.json"
    payload = calibration_artifact(classifier_hash, clip_hash).model_dump(
        mode="json", by_alias=True
    )
    payload["modelVersion"] = "SOME_OTHER_MODEL_VERSION"
    calibration_path.write_text(json.dumps(payload), encoding="utf-8")
    detector = UniversalFakeDetector(
        enabled_settings(
            clip_path,
            classifier_path,
            ufd_calibration_path=calibration_path,
        ),
        clip_loader=lambda _path, _device: FakeClipModel(),
        classifier_loader=lambda _path: classifier_state(0.25),
    )

    result = detector.analyze(sample_image())

    assert result.calibration_status == CalibrationStatus.CALIBRATION_INVALID
    assert result.calibrated_synthetic_probability is None


def test_same_input_is_deterministic_with_deterministic_model(tmp_path: Path) -> None:
    clip_path, classifier_path = checkpoint_files(tmp_path)
    detector = UniversalFakeDetector(
        enabled_settings(clip_path, classifier_path),
        clip_loader=lambda _path, _device: FakeClipModel(0.25),
        classifier_loader=lambda _path: {
            "weight": torch.ones((1, CLIP_FEATURE_DIMENSION)),
            "bias": torch.zeros(1),
        },
    )
    image = sample_image()

    first = detector.analyze(image)
    second = detector.analyze(image)

    assert first.raw_logit == second.raw_logit
    assert first.raw_synthetic_score == second.raw_synthetic_score


def test_preprocessing_is_fixed_clip_normalization() -> None:
    tensor = build_ufd_preprocess()(Image.new("RGB", (400, 300), "white"))

    assert tensor.shape == (3, 224, 224)
    assert tensor[:, 0, 0].tolist() == pytest.approx(
        [
            (1.0 - 0.48145466) / 0.26862954,
            (1.0 - 0.4578275) / 0.26130258,
            (1.0 - 0.40821073) / 0.27577711,
        ]
    )


def enabled_settings(clip_path: Path, classifier_path: Path, **overrides: object) -> Settings:
    return Settings(
        ufd_enabled=True,
        ufd_clip_checkpoint=clip_path,
        ufd_classifier_checkpoint=classifier_path,
        **overrides,
    )


def checkpoint_files(tmp_path: Path) -> tuple[Path, Path]:
    clip_path = tmp_path / "clip.pt"
    classifier_path = tmp_path / "classifier.pth"
    clip_path.write_bytes(b"trusted clip fixture")
    classifier_path.write_bytes(b"trusted classifier fixture")
    return clip_path, classifier_path


def classifier_state(raw_logit: float) -> dict[str, torch.Tensor]:
    return {
        "weight": torch.zeros((1, CLIP_FEATURE_DIMENSION)),
        "bias": torch.tensor([raw_logit]),
    }


def calibration_artifact(
    classifier_hash: str,
    clip_hash: str,
    *,
    a: float = 1.0,
    b: float = 0.0,
) -> UfdCalibrationArtifact:
    return UfdCalibrationArtifact.model_validate(
        {
            "schemaVersion": 1,
            "modelName": "UNIVERSAL_FAKE_DETECT",
            "modelVersion": "UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
            "method": "PLATT_SCALING",
            "a": a,
            "b": b,
            "sampleCount": 20,
            "realCount": 10,
            "syntheticCount": 10,
            "datasetId": "fixture-v1",
            "fittedAt": "2026-01-01T00:00:00Z",
            "modelCheckpointSha256": classifier_hash,
            "clipCheckpointSha256": clip_hash,
        }
    )


def sample_image() -> Image.Image:
    return Image.new("RGB", (256, 256), "navy")


def _unexpected_clip_loader(_path: Path, _device: str) -> None:
    raise AssertionError("CLIP loader must not run")
