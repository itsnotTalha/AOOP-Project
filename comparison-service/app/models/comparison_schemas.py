from enum import Enum

from pydantic import Field

from app.models.base import ApiModel


class ServiceHealthResponse(ApiModel):
    status: str
    service: str


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
