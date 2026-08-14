import base64
from io import BytesIO

import numpy as np
import pytest
import httpx
from PIL import Image, ImageDraw

from app.core.config import Settings
from app.api.routes import get_settings_dependency
from app.main import app


async def small_upload_settings() -> Settings:
    return Settings(max_image_bytes=16)


@pytest.fixture(autouse=True)
def reset_dependency_overrides() -> None:
    app.dependency_overrides.clear()
    yield
    app.dependency_overrides.clear()


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture
async def client():
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://test",
    ) as async_client:
        yield async_client


@pytest.mark.anyio
async def test_identical_images_produce_nearly_empty_reference_sized_mask(
    client: httpx.AsyncClient,
) -> None:
    image = feature_image()

    body = await compare(client, image_bytes(image), image_bytes(image))
    repeated = await compare(client, image_bytes(image), image_bytes(image))

    assert body["status"] == "COMPLETED"
    assert repeated == body
    assert body["difference"]["performed"] is True
    assert body["difference"]["changedAreaRatio"] <= 0.001
    mask = decode_mask(body)
    assert mask.size == image.size
    assert np.count_nonzero(np.asarray(mask)) <= image.width * image.height * 0.001


@pytest.mark.anyio
async def test_resized_same_content_has_low_changed_area(
    client: httpx.AsyncClient,
) -> None:
    reference = feature_image()
    target = reference.resize((480, 360), Image.Resampling.LANCZOS)

    body = await compare(client, image_bytes(reference), image_bytes(target))

    assert body["status"] == "COMPLETED"
    assert body["alignment"]["status"] in {"ALIGNED", "FALLBACK_RESIZE"}
    assert body["difference"]["changedAreaRatio"] < 0.03


@pytest.mark.anyio
async def test_local_edit_creates_mask_overlapping_edited_region(
    client: httpx.AsyncClient,
) -> None:
    reference = feature_image()
    target = reference.copy()
    edited_region = (180, 80, 250, 150)
    ImageDraw.Draw(target).rectangle(edited_region, fill=(245, 30, 35))

    identical = await compare(client, image_bytes(reference), image_bytes(reference))
    edited = await compare(client, image_bytes(reference), image_bytes(target))

    assert edited["status"] == "COMPLETED"
    assert (
        edited["difference"]["changedAreaRatio"]
        > identical["difference"]["changedAreaRatio"]
    )
    mask = np.asarray(decode_mask(edited))
    left, top, right, bottom = edited_region
    overlap_ratio = np.count_nonzero(mask[top:bottom, left:right]) / (
        (right - left) * (bottom - top)
    )
    assert overlap_ratio > 0.5


@pytest.mark.anyio
async def test_small_rotation_uses_homography_and_excludes_false_warp_borders(
    client: httpx.AsyncClient,
) -> None:
    reference = feature_image()
    target = reference.rotate(3.0, resample=Image.Resampling.BICUBIC, expand=False)

    body = await compare(client, image_bytes(reference), image_bytes(target))

    assert body["status"] == "COMPLETED"
    assert body["alignment"]["status"] == "ALIGNED"
    assert body["alignment"]["method"] == "ORB_HOMOGRAPHY"
    assert body["alignment"]["inliers"] >= 8
    assert body["difference"]["changedAreaRatio"] < 0.08


@pytest.mark.anyio
async def test_jpeg_recompression_does_not_create_massive_change_area(
    client: httpx.AsyncClient,
) -> None:
    reference = feature_image()

    body = await compare(
        client,
        image_bytes(reference, "JPEG", quality=95),
        image_bytes(reference, "JPEG", quality=35),
        reference_name="reference.jpg",
        target_name="target.jpg",
    )

    assert body["status"] == "COMPLETED"
    assert body["difference"]["changedAreaRatio"] < 0.05


@pytest.mark.anyio
async def test_unrelated_image_completes_without_claiming_authenticity(
    client: httpx.AsyncClient,
) -> None:
    reference = feature_image()
    target = unrelated_image()

    body = await compare(client, image_bytes(reference), image_bytes(target))

    assert body["status"] == "COMPLETED"
    assert body["alignment"]["status"] in {"ALIGNED", "FALLBACK_RESIZE"}
    assert "fake" not in str(body).lower()
    assert "manipulated" not in str(body).lower()


@pytest.mark.anyio
async def test_low_feature_image_uses_explicit_resize_fallback(
    client: httpx.AsyncClient,
) -> None:
    reference = Image.new("RGB", (320, 240), "navy")
    target = Image.new("RGB", (640, 480), "navy")

    body = await compare(client, image_bytes(reference), image_bytes(target))

    assert body["status"] == "COMPLETED"
    assert body["alignment"]["status"] == "FALLBACK_RESIZE"
    assert body["alignment"]["method"] == "RESIZE"


@pytest.mark.anyio
async def test_compare_endpoint_rejects_corrupt_unsupported_and_oversized_images(
    client: httpx.AsyncClient,
) -> None:
    valid = image_bytes(feature_image())

    corrupt = await client.post(
        "/v1/compare/images",
        files={
            "reference": ("reference.png", valid, "image/png"),
            "target": ("target.png", b"\x89PNG\r\n\x1a\ncorrupt", "image/png"),
        },
    )
    assert corrupt.status_code == 422

    unsupported_buffer = BytesIO()
    feature_image().save(unsupported_buffer, format="GIF")
    unsupported = await client.post(
        "/v1/compare/images",
        files={
            "reference": ("reference.png", valid, "image/png"),
            "target": ("target.png", unsupported_buffer.getvalue(), "image/png"),
        },
    )
    assert unsupported.status_code == 415

    app.dependency_overrides[get_settings_dependency] = small_upload_settings
    oversized = await client.post(
        "/v1/compare/images",
        files={
            "reference": ("reference.png", valid, "image/png"),
            "target": ("target.png", valid, "image/png"),
        },
    )
    assert oversized.status_code == 413


async def compare(
    client: httpx.AsyncClient,
    reference: bytes,
    target: bytes,
    reference_name: str = "reference.png",
    target_name: str = "target.png",
) -> dict:
    response = await client.post(
        "/v1/compare/images",
        files={
            "reference": (reference_name, reference, "text/plain"),
            "target": (target_name, target, "application/octet-stream"),
        },
    )
    assert response.status_code == 200, response.text
    return response.json()


def decode_mask(body: dict) -> Image.Image:
    assert body["mask"]["available"] is True
    assert body["mask"]["format"] == "png"
    return Image.open(BytesIO(base64.b64decode(body["mask"]["base64"])))


def feature_image() -> Image.Image:
    image = Image.new("RGB", (320, 240), (235, 238, 242))
    draw = ImageDraw.Draw(image)
    for x in range(20, 310, 30):
        draw.line((x, 10, x, 230), fill=(70, 75, 85), width=2)
    for y in range(20, 230, 30):
        draw.line((10, y, 310, y), fill=(80, 90, 100), width=2)
    for index in range(18):
        x = 15 + ((index * 47) % 270)
        y = 15 + ((index * 71) % 190)
        color = (
            30 + ((index * 31) % 210),
            25 + ((index * 53) % 210),
            20 + ((index * 79) % 210),
        )
        draw.ellipse((x, y, x + 14, y + 14), fill=color, outline="black", width=2)
    draw.polygon(((35, 200), (95, 135), (150, 210)), fill=(20, 110, 210))
    draw.text((205, 195), "AUTHVAULT", fill=(15, 20, 30))
    return image


def unrelated_image() -> Image.Image:
    image = Image.new("RGB", (320, 240), "white")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            pixels[x, y] = (
                (x * 13 + y * 3) % 256,
                (x * 5 + y * 17) % 256,
                (x * 19 + y * 7) % 256,
            )
    return image


def image_bytes(
    image: Image.Image,
    image_format: str = "PNG",
    **save_options: int,
) -> bytes:
    output = BytesIO()
    image.save(output, format=image_format, **save_options)
    return output.getvalue()
