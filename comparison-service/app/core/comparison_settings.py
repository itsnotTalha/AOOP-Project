from functools import lru_cache
from typing import Self

from pydantic import AliasChoices, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ComparisonSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="AUTHVAULT_",
        env_ignore_empty=True,
        extra="ignore",
        populate_by_name=True,
        str_strip_whitespace=True,
    )

    max_image_bytes: int = Field(
        default=25 * 1024 * 1024,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MAX_IMAGE_BYTES",
            "AUTHVAULT_AI_MAX_IMAGE_BYTES",
        ),
    )
    max_image_pixels: int = Field(
        default=40_000_000,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MAX_IMAGE_PIXELS",
            "AUTHVAULT_AI_MAX_IMAGE_PIXELS",
        ),
    )
    max_mask_bytes: int = Field(
        default=8 * 1024 * 1024,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MAX_MASK_BYTES",
            "AUTHVAULT_AI_COMPARISON_MAX_MASK_BYTES",
        ),
    )
    orb_max_features: int = Field(
        default=2_000,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_ORB_MAX_FEATURES",
            "AUTHVAULT_AI_COMPARISON_ORB_MAX_FEATURES",
        ),
    )
    orb_match_ratio: float = Field(
        default=0.75,
        gt=0.0,
        lt=1.0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_ORB_MATCH_RATIO",
            "AUTHVAULT_AI_COMPARISON_ORB_MATCH_RATIO",
        ),
    )
    min_good_matches: int = Field(
        default=12,
        ge=4,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MIN_GOOD_MATCHES",
            "AUTHVAULT_AI_COMPARISON_MIN_GOOD_MATCHES",
        ),
    )
    min_inliers: int = Field(
        default=8,
        ge=4,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MIN_INLIERS",
            "AUTHVAULT_AI_COMPARISON_MIN_INLIERS",
        ),
    )
    min_inlier_ratio: float = Field(
        default=0.35,
        ge=0.0,
        le=1.0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MIN_INLIER_RATIO",
            "AUTHVAULT_AI_COMPARISON_MIN_INLIER_RATIO",
        ),
    )
    ransac_reprojection_threshold: float = Field(
        default=3.0,
        gt=0.0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_RANSAC_REPROJECTION_THRESHOLD",
            "AUTHVAULT_AI_COMPARISON_RANSAC_REPROJECTION_THRESHOLD",
        ),
    )
    pixel_difference_threshold: int = Field(
        default=24,
        ge=0,
        le=255,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_PIXEL_DIFFERENCE_THRESHOLD",
            "AUTHVAULT_AI_COMPARISON_PIXEL_DIFFERENCE_THRESHOLD",
        ),
    )
    minimum_region_area: int = Field(
        default=25,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MINIMUM_REGION_AREA",
            "AUTHVAULT_AI_COMPARISON_MINIMUM_REGION_AREA",
        ),
    )
    morphology_kernel_size: int = Field(
        default=3,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_MORPHOLOGY_KERNEL_SIZE",
            "AUTHVAULT_AI_COMPARISON_MORPHOLOGY_KERNEL_SIZE",
        ),
    )
    gaussian_blur_kernel_size: int = Field(
        default=3,
        gt=0,
        validation_alias=AliasChoices(
            "AUTHVAULT_IMAGE_COMPARISON_GAUSSIAN_BLUR_KERNEL_SIZE",
            "AUTHVAULT_AI_COMPARISON_GAUSSIAN_BLUR_KERNEL_SIZE",
        ),
    )

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
def get_comparison_settings() -> ComparisonSettings:
    return ComparisonSettings()
