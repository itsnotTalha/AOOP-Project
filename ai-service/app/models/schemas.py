from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
    )


class AnalysisStatus(str, Enum):
    MODEL_NOT_CONFIGURED = "MODEL_NOT_CONFIGURED"
    MODEL_INVALID = "MODEL_INVALID"
    DEVICE_UNAVAILABLE = "DEVICE_UNAVAILABLE"
    PROCESSING_FAILED = "PROCESSING_FAILED"
    PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT"
    COMPLETED = "COMPLETED"


class ModelSignal(str, Enum):
    REAL_LEANING = "REAL_LEANING"
    SYNTHETIC_LEANING = "SYNTHETIC_LEANING"


class ManipulationSignal(str, Enum):
    LOW_MANIPULATION_SIGNAL = "LOW_MANIPULATION_SIGNAL"
    ELEVATED_MANIPULATION_SIGNAL = "ELEVATED_MANIPULATION_SIGNAL"


class CalibrationStatus(str, Enum):
    NOT_CALIBRATED = "NOT_CALIBRATED"
    CALIBRATED = "CALIBRATED"
    CALIBRATION_INVALID = "CALIBRATION_INVALID"


class ModelHealth(ApiModel):
    configured: bool
    ready: bool
    model_name: str | None = None
    model_version: str | None = None


class HealthModels(ApiModel):
    ai_generation: ModelHealth
    manipulation: ModelHealth


class HealthResponse(ApiModel):
    status: str
    service: str
    models_ready: bool
    models: HealthModels


class AiGenerationResult(ApiModel):
    performed: bool
    model_name: str | None = None
    model_version: str | None = None
    raw_logit: float | None = None
    raw_synthetic_score: float | None = Field(default=None, ge=0.0, le=1.0)
    decision_threshold: float | None = Field(default=None, gt=0.0, lt=1.0)
    model_signal: ModelSignal | None = None
    calibration_status: CalibrationStatus | None = None
    calibrated_synthetic_probability: float | None = Field(
        default=None, ge=0.0, le=1.0
    )
    status: str


class ManipulationResult(ApiModel):
    performed: bool
    model_name: str | None = None
    model_version: str | None = None
    manipulation_score: float | None = Field(default=None, ge=0.0, le=1.0)
    decision_threshold: float | None = Field(default=None, gt=0.0, lt=1.0)
    model_signal: ManipulationSignal | None = None
    localization_available: bool = False
    suspicious_area_ratio: float | None = Field(default=None, ge=0.0, le=1.0)
    reliable_suspicious_area_ratio: float | None = Field(
        default=None, ge=0.0, le=1.0
    )
    anomaly_map_png_base64: str | None = None
    reliability_map_png_base64: str | None = None
    suspicious_mask_png_base64: str | None = None
    status: str


class ImageAnalysisResponse(ApiModel):
    analysis_version: str
    ai_generation: AiGenerationResult
    manipulation: ManipulationResult


class AlignmentStatus(str, Enum):
    ALIGNED = "ALIGNED"
    FALLBACK_RESIZE = "FALLBACK_RESIZE"
    ALIGNMENT_FAILED = "ALIGNMENT_FAILED"


class ImageComparisonStatus(str, Enum):
    COMPLETED = "COMPLETED"
    NO_VALID_OVERLAP = "NO_VALID_OVERLAP"
    PROCESSING_FAILED = "PROCESSING_FAILED"


class AlignmentResult(ApiModel):
    status: AlignmentStatus
    method: str | None = None
    keypoints_reference: int = Field(ge=0)
    keypoints_target: int = Field(ge=0)
    good_matches: int = Field(ge=0)
    inliers: int = Field(ge=0)
    inlier_ratio: float | None = Field(default=None, ge=0.0, le=1.0)


class DifferenceResult(ApiModel):
    performed: bool
    changed_area_ratio: float | None = Field(default=None, ge=0.0, le=1.0)
    mean_absolute_difference: float | None = Field(default=None, ge=0.0, le=255.0)
    structural_similarity: float | None = Field(default=None, ge=-1.0, le=1.0)


class ChangeMaskResult(ApiModel):
    available: bool
    format: str | None = None
    base64_png: str | None = Field(default=None, alias="base64")


class ImageComparisonResponse(ApiModel):
    comparison_version: str
    alignment: AlignmentResult
    difference: DifferenceResult
    mask: ChangeMaskResult
    status: ImageComparisonStatus
