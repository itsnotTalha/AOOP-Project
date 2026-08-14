import os
from functools import lru_cache
from pathlib import Path
from typing import Literal
from typing import Self

from pydantic import BaseModel, Field, model_validator


class Settings(BaseModel):
    max_image_bytes: int = Field(default=25 * 1024 * 1024, gt=0)
    max_image_pixels: int = Field(default=40_000_000, gt=0)
    max_mask_bytes: int = Field(default=8 * 1024 * 1024, gt=0)
    orb_max_features: int = Field(default=2_000, gt=0)
    orb_match_ratio: float = Field(default=0.75, gt=0.0, lt=1.0)
    min_good_matches: int = Field(default=12, ge=4)
    min_inliers: int = Field(default=8, ge=4)
    min_inlier_ratio: float = Field(default=0.35, ge=0.0, le=1.0)
    ransac_reprojection_threshold: float = Field(default=3.0, gt=0.0)
    pixel_difference_threshold: int = Field(default=24, ge=0, le=255)
    minimum_region_area: int = Field(default=25, gt=0)
    morphology_kernel_size: int = Field(default=3, gt=0)
    gaussian_blur_kernel_size: int = Field(default=3, gt=0)
    ufd_enabled: bool = False
    ufd_clip_checkpoint: Path | None = None
    ufd_classifier_checkpoint: Path | None = None
    ufd_device: Literal["auto", "cpu", "cuda"] = "auto"
    ufd_calibration_path: Path | None = None
    ufd_clip_sha256: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    ufd_classifier_sha256: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    ufd_decision_threshold: float = Field(default=0.5, gt=0.0, lt=1.0)
    ufd_max_concurrent_inference: int = Field(default=1, gt=0)
    ufd_minimum_calibration_samples: int = Field(default=20, ge=2)
    trufor_enabled: bool = False
    trufor_model_path: Path | None = None
    trufor_model_sha256: str | None = Field(
        default=None, pattern=r"^[0-9a-f]{64}$"
    )
    trufor_runtime_path: Path | None = None
    trufor_runtime_revision: str = "ae54475df6f41a491d7615100feb19263dec13f7"
    trufor_device: Literal["auto", "cpu", "cuda"] = "auto"
    trufor_max_concurrent_inference: int = Field(default=1, gt=0)
    trufor_inference_timeout_seconds: float = Field(default=60.0, gt=0.0)
    trufor_score_threshold: float = Field(default=0.5, gt=0.0, lt=1.0)
    trufor_anomaly_threshold: float = Field(default=0.5, ge=0.0, le=1.0)
    trufor_min_reliability: float = Field(default=0.5, ge=0.0, le=1.0)
    trufor_max_map_dimension: int = Field(default=1024, gt=0)
    trufor_max_map_bytes: int = Field(default=8 * 1024 * 1024, gt=0)

    @model_validator(mode="after")
    def validate_comparison_configuration(self) -> Self:
        if self.min_inliers > self.min_good_matches:
            raise ValueError("minimum inliers cannot exceed minimum good matches")
        if self.morphology_kernel_size % 2 == 0:
            raise ValueError("morphology kernel size must be odd")
        if self.gaussian_blur_kernel_size % 2 == 0:
            raise ValueError("Gaussian blur kernel size must be odd")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings(
        max_image_bytes=int(
            os.getenv("AUTHVAULT_AI_MAX_IMAGE_BYTES", str(25 * 1024 * 1024))
        ),
        max_image_pixels=int(
            os.getenv("AUTHVAULT_AI_MAX_IMAGE_PIXELS", "40000000")
        ),
        max_mask_bytes=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_MAX_MASK_BYTES", str(8 * 1024 * 1024))
        ),
        orb_max_features=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_ORB_MAX_FEATURES", "2000")
        ),
        orb_match_ratio=float(
            os.getenv("AUTHVAULT_AI_COMPARISON_ORB_MATCH_RATIO", "0.75")
        ),
        min_good_matches=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_MIN_GOOD_MATCHES", "12")
        ),
        min_inliers=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_MIN_INLIERS", "8")
        ),
        min_inlier_ratio=float(
            os.getenv("AUTHVAULT_AI_COMPARISON_MIN_INLIER_RATIO", "0.35")
        ),
        ransac_reprojection_threshold=float(
            os.getenv("AUTHVAULT_AI_COMPARISON_RANSAC_REPROJECTION_THRESHOLD", "3.0")
        ),
        pixel_difference_threshold=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_PIXEL_DIFFERENCE_THRESHOLD", "24")
        ),
        minimum_region_area=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_MINIMUM_REGION_AREA", "25")
        ),
        morphology_kernel_size=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_MORPHOLOGY_KERNEL_SIZE", "3")
        ),
        gaussian_blur_kernel_size=int(
            os.getenv("AUTHVAULT_AI_COMPARISON_GAUSSIAN_BLUR_KERNEL_SIZE", "3")
        ),
        ufd_enabled=_environment_boolean("AUTHVAULT_UFD_ENABLED", False),
        ufd_clip_checkpoint=_environment_path("AUTHVAULT_UFD_CLIP_CHECKPOINT"),
        ufd_classifier_checkpoint=_environment_path(
            "AUTHVAULT_UFD_CLASSIFIER_CHECKPOINT"
        ),
        ufd_device=os.getenv("AUTHVAULT_UFD_DEVICE", "auto").strip().lower(),
        ufd_calibration_path=_environment_path("AUTHVAULT_UFD_CALIBRATION_PATH"),
        ufd_clip_sha256=_environment_optional("AUTHVAULT_UFD_CLIP_SHA256"),
        ufd_classifier_sha256=_environment_optional(
            "AUTHVAULT_UFD_CLASSIFIER_SHA256"
        ),
        ufd_decision_threshold=float(
            os.getenv("AUTHVAULT_UFD_DECISION_THRESHOLD", "0.5")
        ),
        ufd_max_concurrent_inference=int(
            os.getenv("AUTHVAULT_UFD_MAX_CONCURRENT_INFERENCE", "1")
        ),
        ufd_minimum_calibration_samples=int(
            os.getenv("AUTHVAULT_UFD_MINIMUM_CALIBRATION_SAMPLES", "20")
        ),
        trufor_enabled=_environment_boolean("AUTHVAULT_TRUFOR_ENABLED", False),
        trufor_model_path=_environment_path("AUTHVAULT_TRUFOR_MODEL_PATH"),
        trufor_model_sha256=_environment_optional("AUTHVAULT_TRUFOR_MODEL_SHA256"),
        trufor_runtime_path=_environment_path("AUTHVAULT_TRUFOR_RUNTIME_PATH"),
        trufor_runtime_revision=os.getenv(
            "AUTHVAULT_TRUFOR_RUNTIME_REVISION",
            "ae54475df6f41a491d7615100feb19263dec13f7",
        ).strip().lower(),
        trufor_device=os.getenv("AUTHVAULT_TRUFOR_DEVICE", "auto").strip().lower(),
        trufor_max_concurrent_inference=int(
            os.getenv("AUTHVAULT_TRUFOR_MAX_CONCURRENT_INFERENCE", "1")
        ),
        trufor_inference_timeout_seconds=float(
            os.getenv("AUTHVAULT_TRUFOR_INFERENCE_TIMEOUT_SECONDS", "60")
        ),
        trufor_score_threshold=float(
            os.getenv("AUTHVAULT_TRUFOR_SCORE_THRESHOLD", "0.5")
        ),
        trufor_anomaly_threshold=float(
            os.getenv("AUTHVAULT_TRUFOR_ANOMALY_THRESHOLD", "0.5")
        ),
        trufor_min_reliability=float(
            os.getenv("AUTHVAULT_TRUFOR_MIN_RELIABILITY", "0.5")
        ),
        trufor_max_map_dimension=int(
            os.getenv("AUTHVAULT_TRUFOR_MAX_MAP_DIMENSION", "1024")
        ),
        trufor_max_map_bytes=int(
            os.getenv("AUTHVAULT_TRUFOR_MAX_MAP_BYTES", str(8 * 1024 * 1024))
        ),
    )


def _environment_optional(name: str) -> str | None:
    value = os.getenv(name)
    if value is None or not value.strip():
        return None
    return value.strip().lower()


def _environment_path(name: str) -> Path | None:
    value = os.getenv(name)
    if value is None or not value.strip():
        return None
    return Path(value.strip())


def _environment_boolean(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"{name} must be a boolean value")
