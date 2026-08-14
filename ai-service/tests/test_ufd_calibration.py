import math
from pathlib import Path

import pytest

from app.calibration.ufd import (
    UfdCalibrationArtifact,
    create_calibration_artifact,
    fit_platt_calibration,
    load_calibration_artifact,
    save_calibration_artifact,
)


def test_fit_requires_both_classes() -> None:
    with pytest.raises(ValueError, match="both real and synthetic"):
        fit_platt_calibration([-2.0, -1.0, -0.5], [0, 0, 0], 3)


def test_fit_rejects_insufficient_samples() -> None:
    with pytest.raises(ValueError, match="insufficient"):
        fit_platt_calibration([-1.0, 1.0], [0, 1], 3)


def test_fit_is_deterministic_finite_and_probability_is_bounded() -> None:
    logits = [-3.0, -2.0, -1.0, -0.25, 0.25, 1.0, 2.0, 3.0]
    labels = [0, 0, 0, 0, 1, 1, 1, 1]

    first = fit_platt_calibration(logits, labels, 8)
    second = fit_platt_calibration(logits, labels, 8)

    assert first == pytest.approx(second)
    assert all(math.isfinite(value) for value in first)
    artifact = artifact_with(first[0], first[1])
    assert 0.0 <= artifact.probability(100.0) <= 1.0
    assert 0.0 <= artifact.probability(-100.0) <= 1.0


def test_artifact_round_trip_and_no_overwrite(tmp_path: Path) -> None:
    output = tmp_path / "calibration.json"
    artifact = artifact_with(1.5, -0.25)

    save_calibration_artifact(artifact, output)
    loaded = load_calibration_artifact(output)

    assert loaded == artifact
    with pytest.raises(FileExistsError):
        save_calibration_artifact(artifact, output)
    save_calibration_artifact(artifact, output, overwrite=True)


def test_artifact_rejects_nonfinite_parameters_and_bad_counts() -> None:
    payload = artifact_with(1.0, 0.0).model_dump()
    payload["a"] = float("nan")
    with pytest.raises(ValueError):
        UfdCalibrationArtifact.model_validate(payload)

    payload = artifact_with(1.0, 0.0).model_dump()
    payload["sample_count"] = 21
    with pytest.raises(ValueError):
        UfdCalibrationArtifact.model_validate(payload)


def artifact_with(a: float, b: float) -> UfdCalibrationArtifact:
    return create_calibration_artifact(
        a=a,
        b=b,
        labels=[0] * 10 + [1] * 10,
        dataset_id="synthetic-unit-fixture",
        model_name="UNIVERSAL_FAKE_DETECT",
        model_version="UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
        model_checkpoint_sha256="a" * 64,
        clip_checkpoint_sha256="b" * 64,
    )
