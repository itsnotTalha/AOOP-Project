from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile

from app.api.image_inputs import decode_supported_image, read_controlled_image
from app.core.comparison_settings import ComparisonSettings, get_comparison_settings
from app.models.comparison_schemas import ImageComparisonResponse, ServiceHealthResponse
from app.services.image_comparison_service import (
    ImageComparisonService,
    get_image_comparison_service,
)


router = APIRouter()


async def get_comparison_settings_dependency() -> ComparisonSettings:
    return get_comparison_settings()


async def _comparison_service_dependency() -> ImageComparisonService:
    return get_image_comparison_service()


ComparisonSettingsDependency = Annotated[
    ComparisonSettings,
    Depends(get_comparison_settings_dependency),
]
ComparisonServiceDependency = Annotated[
    ImageComparisonService,
    Depends(_comparison_service_dependency),
]


@router.get("/health", response_model=ServiceHealthResponse)
async def health() -> ServiceHealthResponse:
    return ServiceHealthResponse(status="ok", service="authvault-image-comparison")


@router.post(
    "/v1/compare/images",
    response_model=ImageComparisonResponse,
)
async def compare_images(
    reference: Annotated[UploadFile, File(...)],
    target: Annotated[UploadFile, File(...)],
    settings: ComparisonSettingsDependency,
    service: ComparisonServiceDependency,
) -> ImageComparisonResponse:
    reference_bytes = await read_controlled_image(reference, settings.max_image_bytes)
    target_bytes = await read_controlled_image(target, settings.max_image_bytes)
    reference_image = decode_supported_image(reference_bytes, settings.max_image_pixels)
    try:
        target_image = decode_supported_image(target_bytes, settings.max_image_pixels)
    except Exception:
        reference_image.close()
        raise
    try:
        return service.compare(reference_image, target_image)
    finally:
        reference_image.close()
        target_image.close()
