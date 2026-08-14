from io import BytesIO
from tempfile import SpooledTemporaryFile
from typing import Annotated
import warnings

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from PIL import Image, ImageOps, UnidentifiedImageError

from app.core.config import Settings, get_settings
from app.models.schemas import HealthResponse, ImageAnalysisResponse, ImageComparisonResponse
from app.services.forensic_service import ForensicAnalysisService, get_forensic_service
from app.services.image_comparison_service import (
    ImageComparisonService,
    get_image_comparison_service,
)

router = APIRouter()


async def get_settings_dependency() -> Settings:
    return get_settings()


async def _forensic_service_dependency() -> ForensicAnalysisService:
    return get_forensic_service()


async def _comparison_service_dependency() -> ImageComparisonService:
    return get_image_comparison_service()


SettingsDependency = Annotated[Settings, Depends(get_settings_dependency)]
ForensicServiceDependency = Annotated[
    ForensicAnalysisService,
    Depends(_forensic_service_dependency),
]
ComparisonServiceDependency = Annotated[
    ImageComparisonService,
    Depends(_comparison_service_dependency),
]

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
JPEG_SIGNATURE = b"\xff\xd8\xff"
READ_CHUNK_SIZE = 64 * 1024


@router.get("/health", response_model=HealthResponse)
async def health(service: ForensicServiceDependency) -> HealthResponse:
    return HealthResponse(
        status="ok",
        service="authvault-ai",
        models_ready=service.models_ready,
        models=service.model_health,
    )


@router.post(
    "/v1/analyze/image",
    response_model=ImageAnalysisResponse,
    response_model_exclude_none=True,
)
async def analyze_image(
    file: Annotated[UploadFile, File(...)],
    settings: SettingsDependency,
    service: ForensicServiceDependency,
) -> ImageAnalysisResponse:
    image_bytes = await _read_controlled_image(file, settings.max_image_bytes)
    image = _decode_supported_image(image_bytes, settings.max_image_pixels)
    try:
        return service.analyze(image)
    finally:
        image.close()


@router.post(
    "/v1/compare/images",
    response_model=ImageComparisonResponse,
)
async def compare_images(
    reference: Annotated[UploadFile, File(...)],
    target: Annotated[UploadFile, File(...)],
    settings: SettingsDependency,
    service: ComparisonServiceDependency,
) -> ImageComparisonResponse:
    reference_bytes = await _read_controlled_image(reference, settings.max_image_bytes)
    target_bytes = await _read_controlled_image(target, settings.max_image_bytes)
    reference_image = _decode_supported_image(reference_bytes, settings.max_image_pixels)
    try:
        target_image = _decode_supported_image(target_bytes, settings.max_image_pixels)
    except Exception:
        reference_image.close()
        raise
    try:
        return service.compare(reference_image, target_image)
    finally:
        reference_image.close()
        target_image.close()


async def _read_controlled_image(file: UploadFile, maximum_bytes: int) -> bytes:
    size = 0
    with SpooledTemporaryFile(max_size=maximum_bytes, mode="w+b") as buffer:
        try:
            while chunk := await file.read(READ_CHUNK_SIZE):
                size += len(chunk)
                if size > maximum_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                        detail="Image exceeds the configured size limit.",
                    )
                buffer.write(chunk)
        finally:
            await file.close()

        if size == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Image file is required and must not be empty.",
            )

        buffer.seek(0)
        return buffer.read()


def _decode_supported_image(image_bytes: bytes, maximum_pixels: int) -> Image.Image:
    expected_format = _format_from_signature(image_bytes)
    if expected_format is None:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Only JPEG and PNG images are supported.",
        )

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(image_bytes)) as candidate:
                if candidate.format != expected_format:
                    raise HTTPException(
                        status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                        detail="Image content does not match a supported format.",
                    )
                width, height = candidate.size
                if width <= 0 or height <= 0 or width * height > maximum_pixels:
                    raise HTTPException(
                        status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                        detail="Image dimensions are invalid or exceed the safe limit.",
                    )
                candidate.verify()

            with Image.open(BytesIO(image_bytes)) as decoded:
                if decoded.format != expected_format:
                    raise HTTPException(
                        status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                        detail="Image content does not match a supported format.",
                    )
                normalized = ImageOps.exif_transpose(decoded).convert("RGB")
                normalized.load()
                return normalized
    except HTTPException:
        raise
    except (
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
        UnidentifiedImageError,
        OSError,
        SyntaxError,
        ValueError,
    ) as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail="Image is malformed or could not be decoded.",
        ) from exc


def _format_from_signature(image_bytes: bytes) -> str | None:
    if image_bytes.startswith(PNG_SIGNATURE):
        return "PNG"
    if image_bytes.startswith(JPEG_SIGNATURE):
        return "JPEG"
    return None
