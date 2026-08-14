import json
import math
from datetime import datetime, timezone
from pathlib import Path

import torch
from pydantic import BaseModel, ConfigDict, Field, model_validator


def _camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class UfdCalibrationArtifact(BaseModel):
    model_config = ConfigDict(
        alias_generator=_camel,
        populate_by_name=True,
        extra="forbid",
    )

    schema_version: int = Field(default=1, ge=1, le=1)
    model_name: str
    model_version: str
    method: str = Field(default="PLATT_SCALING", pattern=r"^PLATT_SCALING$")
    a: float
    b: float
    sample_count: int = Field(gt=1)
    real_count: int = Field(gt=0)
    synthetic_count: int = Field(gt=0)
    dataset_id: str = Field(min_length=1, max_length=200)
    fitted_at: datetime
    model_checkpoint_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    clip_checkpoint_sha256: str | None = Field(
        default=None, pattern=r"^[0-9a-f]{64}$"
    )

    @model_validator(mode="after")
    def validate_counts_and_parameters(self) -> "UfdCalibrationArtifact":
        if self.real_count + self.synthetic_count != self.sample_count:
            raise ValueError("calibration class counts must equal sample count")
        if not math.isfinite(self.a) or not math.isfinite(self.b):
            raise ValueError("calibration parameters must be finite")
        return self

    def probability(self, raw_logit: float) -> float:
        if not math.isfinite(raw_logit):
            raise ValueError("raw logit must be finite")
        return torch.sigmoid(
            torch.tensor(self.a * raw_logit + self.b, dtype=torch.float64)
        ).item()


def load_calibration_artifact(path: Path) -> UfdCalibrationArtifact:
    if not path.is_file():
        raise ValueError("calibration artifact is unavailable")
    try:
        content = path.read_text(encoding="utf-8")
        return UfdCalibrationArtifact.model_validate_json(content)
    except (OSError, UnicodeError, ValueError) as exception:
        raise ValueError("calibration artifact is invalid") from exception


def fit_platt_calibration(
    logits: list[float],
    labels: list[int],
    minimum_samples: int,
) -> tuple[float, float]:
    if len(logits) != len(labels):
        raise ValueError("logits and labels must have equal lengths")
    if len(logits) < minimum_samples:
        raise ValueError("insufficient calibration samples")
    if set(labels) != {0, 1}:
        raise ValueError("calibration requires both real and synthetic classes")
    if any(not math.isfinite(value) for value in logits):
        raise ValueError("calibration logits must be finite")
    if any(label not in {0, 1} for label in labels):
        raise ValueError("calibration labels must be binary")

    x = torch.tensor(logits, dtype=torch.float64)
    y = torch.tensor(labels, dtype=torch.float64)
    a = torch.nn.Parameter(torch.tensor(1.0, dtype=torch.float64))
    b = torch.nn.Parameter(torch.tensor(0.0, dtype=torch.float64))
    optimizer = torch.optim.LBFGS(
        [a, b], lr=0.25, max_iter=250, tolerance_grad=1e-12, tolerance_change=1e-14
    )

    def closure() -> torch.Tensor:
        optimizer.zero_grad()
        loss = torch.nn.functional.binary_cross_entropy_with_logits(a * x + b, y)
        loss.backward()
        return loss

    optimizer.step(closure)
    fitted_a = float(a.detach().item())
    fitted_b = float(b.detach().item())
    if not math.isfinite(fitted_a) or not math.isfinite(fitted_b):
        raise ValueError("calibration fitting produced invalid parameters")
    return fitted_a, fitted_b


def create_calibration_artifact(
    *,
    a: float,
    b: float,
    labels: list[int],
    dataset_id: str,
    model_name: str,
    model_version: str,
    model_checkpoint_sha256: str,
    clip_checkpoint_sha256: str,
) -> UfdCalibrationArtifact:
    return UfdCalibrationArtifact(
        model_name=model_name,
        model_version=model_version,
        a=a,
        b=b,
        sample_count=len(labels),
        real_count=labels.count(0),
        synthetic_count=labels.count(1),
        dataset_id=dataset_id,
        fitted_at=datetime.now(timezone.utc),
        model_checkpoint_sha256=model_checkpoint_sha256,
        clip_checkpoint_sha256=clip_checkpoint_sha256,
    )


def save_calibration_artifact(
    artifact: UfdCalibrationArtifact,
    output: Path,
    *,
    overwrite: bool = False,
) -> None:
    if output.exists() and not overwrite:
        raise FileExistsError("calibration output already exists")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    serialized = json.dumps(
        artifact.model_dump(mode="json", by_alias=True), indent=2, sort_keys=True
    )
    try:
        temporary.write_text(serialized + "\n", encoding="utf-8")
        temporary.replace(output)
    finally:
        if temporary.exists():
            temporary.unlink()


def brier_score(probabilities: list[float], labels: list[int]) -> float:
    if not probabilities or len(probabilities) != len(labels):
        raise ValueError("probabilities and labels must be non-empty and equal length")
    return sum(
        (probability - label) ** 2
        for probability, label in zip(probabilities, labels, strict=True)
    ) / len(labels)
