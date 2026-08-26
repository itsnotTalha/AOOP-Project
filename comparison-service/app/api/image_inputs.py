from io import BytesIO
from tempfile import SpooledTemporaryFile
import warnings

from fastapi import HTTPException, UploadFile, status
from PIL import Image, ImageOps, UnidentifiedImageError


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
JPEG_SIGNATURE = b"\xff\xd8\xff"
READ_CHUNK_SIZE = 64 * 1024


async def read_controlled_image(file: UploadFile, maximum_bytes: int) -> bytes:
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


def decode_supported_image(image_bytes: bytes, maximum_pixels: int) -> Image.Image:
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
