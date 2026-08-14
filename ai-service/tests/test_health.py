import httpx
import pytest

from app.main import app


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.mark.anyio
async def test_health_reports_models_not_ready() -> None:
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "authvault-ai",
        "modelsReady": False,
        "models": {
            "aiGeneration": {
                "configured": False,
                "ready": False,
                "modelName": "UNIVERSAL_FAKE_DETECT",
                "modelVersion": "UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
            },
            "manipulation": {
                "configured": False,
                "ready": False,
                "modelName": "TRUFOR",
                "modelVersion": "TRUFOR_CVPR2023_RELEASED_V1",
            },
        },
    }
