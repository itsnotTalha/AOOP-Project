from io import BytesIO

import pytest
import httpx
from PIL import Image

from app.core.config import Settings
from app.api.routes import get_settings_dependency
from app.api.routes import _forensic_service_dependency
from app.detectors.base import AiGenerationDetector, ManipulationDetector
from app.main import app
from app.models.schemas import AiGenerationResult, ManipulationResult
from app.services.forensic_service import ForensicAnalysisService


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


@pytest.mark.parametrize(
    ("image_format", "filename", "content_type"),
    [
        ("PNG", "sample.png", "text/plain"),
        ("JPEG", "sample.jpg", "application/octet-stream"),
    ],
)
@pytest.mark.anyio
async def test_supported_image_returns_model_not_configured_without_fake_probabilities(
    client: httpx.AsyncClient,
    image_format: str,
    filename: str,
    content_type: str,
) -> None:
    response = await client.post(
        "/v1/analyze/image",
        files={"file": (filename, image_bytes(image_format), content_type)},
    )

    assert response.status_code == 200
    body = response.json()
    assert body == {
        "analysisVersion": "1",
        "aiGeneration": {
            "performed": False,
            "status": "MODEL_NOT_CONFIGURED",
        },
        "manipulation": {
            "performed": False,
            "localizationAvailable": False,
            "status": "MODEL_NOT_CONFIGURED",
        },
    }
    assert "syntheticProbability" not in body["aiGeneration"]
    assert "manipulationProbability" not in body["manipulation"]
    assert "reliabilityScore" not in body["manipulation"]


@pytest.mark.anyio
async def test_unsupported_actual_content_is_rejected(client: httpx.AsyncClient) -> None:
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.png", image_bytes("GIF"), "image/png")},
    )

    assert response.status_code == 415


@pytest.mark.anyio
async def test_corrupt_supported_image_is_rejected(client: httpx.AsyncClient) -> None:
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.jpg", b"\xff\xd8\xffcorrupt", "image/jpeg")},
    )

    assert response.status_code == 422


@pytest.mark.anyio
async def test_oversized_image_is_rejected(client: httpx.AsyncClient) -> None:
    app.dependency_overrides[get_settings_dependency] = small_upload_settings

    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.png", image_bytes("PNG"), "image/png")},
    )

    assert response.status_code == 413


@pytest.mark.anyio
async def test_empty_image_is_rejected(client: httpx.AsyncClient) -> None:
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("empty.png", b"", "image/png")},
    )

    assert response.status_code == 400


@pytest.mark.anyio
async def test_configured_global_detector_response_keeps_manipulation_unavailable(
    client: httpx.AsyncClient,
) -> None:
    class CompletedDetector(AiGenerationDetector):
        def analyze(self, image: Image.Image) -> AiGenerationResult:
            return AiGenerationResult(
                performed=True,
                status="COMPLETED",
                model_name="UNIVERSAL_FAKE_DETECT",
                model_version="UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
                raw_logit=0.0,
                raw_synthetic_score=0.5,
                decision_threshold=0.5,
                model_signal="SYNTHETIC_LEANING",
                calibration_status="NOT_CALIBRATED",
            )

    async def configured_service() -> ForensicAnalysisService:
        return ForensicAnalysisService(ai_generation_detector=CompletedDetector())

    app.dependency_overrides[_forensic_service_dependency] = configured_service
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.png", image_bytes("PNG"), "image/png")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["aiGeneration"] == {
        "performed": True,
        "status": "COMPLETED",
        "modelName": "UNIVERSAL_FAKE_DETECT",
        "modelVersion": "UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
        "rawLogit": 0.0,
        "rawSyntheticScore": 0.5,
        "decisionThreshold": 0.5,
        "modelSignal": "SYNTHETIC_LEANING",
        "calibrationStatus": "NOT_CALIBRATED",
    }
    assert body["manipulation"] == {
        "performed": False,
        "localizationAvailable": False,
        "status": "MODEL_NOT_CONFIGURED",
    }


@pytest.mark.anyio
async def test_ufd_success_is_preserved_when_trufor_fails(
    client: httpx.AsyncClient,
) -> None:
    class SuccessfulUfd(AiGenerationDetector):
        def analyze(self, image: Image.Image) -> AiGenerationResult:
            return AiGenerationResult(
                performed=True,
                status="COMPLETED",
                model_name="UNIVERSAL_FAKE_DETECT",
                model_version="UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
                raw_logit=-1.0,
                raw_synthetic_score=0.268941,
                decision_threshold=0.5,
                model_signal="REAL_LEANING",
                calibration_status="NOT_CALIBRATED",
            )

    class FailedTruFor(ManipulationDetector):
        def analyze(self, image: Image.Image) -> ManipulationResult:
            return ManipulationResult(
                performed=False,
                status="PROCESSING_FAILED",
                model_name="TRUFOR",
                model_version="TRUFOR_CVPR2023_RELEASED_V1",
                localization_available=False,
            )

    async def mixed_service() -> ForensicAnalysisService:
        return ForensicAnalysisService(SuccessfulUfd(), FailedTruFor())

    app.dependency_overrides[_forensic_service_dependency] = mixed_service
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.png", image_bytes("PNG"), "image/png")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["aiGeneration"]["performed"] is True
    assert body["manipulation"] == {
        "performed": False,
        "modelName": "TRUFOR",
        "modelVersion": "TRUFOR_CVPR2023_RELEASED_V1",
        "localizationAvailable": False,
        "status": "PROCESSING_FAILED",
    }


@pytest.mark.anyio
async def test_trufor_success_is_preserved_when_ufd_is_unavailable(
    client: httpx.AsyncClient,
) -> None:
    class UnavailableUfd(AiGenerationDetector):
        def analyze(self, image: Image.Image) -> AiGenerationResult:
            return AiGenerationResult(performed=False, status="MODEL_NOT_CONFIGURED")

    class SuccessfulTruFor(ManipulationDetector):
        def analyze(self, image: Image.Image) -> ManipulationResult:
            return ManipulationResult(
                performed=True,
                status="COMPLETED",
                model_name="TRUFOR",
                model_version="TRUFOR_CVPR2023_RELEASED_V1",
                manipulation_score=0.8,
                decision_threshold=0.5,
                model_signal="ELEVATED_MANIPULATION_SIGNAL",
                localization_available=True,
                suspicious_area_ratio=0.25,
                reliable_suspicious_area_ratio=0.2,
                anomaly_map_png_base64="YW5vbWFseQ==",
                reliability_map_png_base64="cmVsaWFiaWxpdHk=",
                suspicious_mask_png_base64="bWFzaw==",
            )

    async def mixed_service() -> ForensicAnalysisService:
        return ForensicAnalysisService(UnavailableUfd(), SuccessfulTruFor())

    app.dependency_overrides[_forensic_service_dependency] = mixed_service
    response = await client.post(
        "/v1/analyze/image",
        files={"file": ("sample.png", image_bytes("PNG"), "image/png")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["aiGeneration"] == {
        "performed": False,
        "status": "MODEL_NOT_CONFIGURED",
    }
    assert body["manipulation"]["performed"] is True
    assert body["manipulation"]["manipulationScore"] == 0.8
    assert body["manipulation"]["suspiciousMaskPngBase64"] == "bWFzaw=="


def image_bytes(image_format: str) -> bytes:
    output = BytesIO()
    image = Image.new("RGB", (4, 4), color="navy")
    image.save(output, format=image_format)
    image.close()
    return output.getvalue()
