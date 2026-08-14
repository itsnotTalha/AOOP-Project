from functools import lru_cache

from PIL import Image

from app.core.config import get_settings
from app.detectors.base import AiGenerationDetector, ManipulationDetector
from app.detectors.universal_fake_detector import UniversalFakeDetector
from app.detectors.trufor_detector import TruForDetector
from app.models.schemas import (
    AiGenerationResult,
    AnalysisStatus,
    HealthModels,
    ImageAnalysisResponse,
    ModelHealth,
    ManipulationResult,
)


class ForensicAnalysisService:
    def __init__(
        self,
        ai_generation_detector: AiGenerationDetector | None = None,
        manipulation_detector: ManipulationDetector | None = None,
    ) -> None:
        self._ai_generation_detector = ai_generation_detector
        self._manipulation_detector = manipulation_detector

    @property
    def models_ready(self) -> bool:
        configured_models = [
            model
            for model in (
                self.ai_generation_model_health,
                self.manipulation_model_health,
            )
            if model.configured
        ]
        return bool(configured_models) and all(model.ready for model in configured_models)

    @property
    def model_health(self) -> HealthModels:
        return HealthModels(
            ai_generation=self.ai_generation_model_health,
            manipulation=self.manipulation_model_health,
        )

    @property
    def ai_generation_model_health(self) -> ModelHealth:
        if self._ai_generation_detector is None:
            return ModelHealth(configured=False, ready=False)
        health = getattr(self._ai_generation_detector, "model_health", None)
        if isinstance(health, ModelHealth):
            return health
        return ModelHealth(configured=True, ready=True)

    @property
    def manipulation_model_health(self) -> ModelHealth:
        if self._manipulation_detector is None:
            return ModelHealth(configured=False, ready=False)
        health = getattr(self._manipulation_detector, "model_health", None)
        if isinstance(health, ModelHealth):
            return health
        return ModelHealth(configured=True, ready=True)

    def analyze(self, image: Image.Image) -> ImageAnalysisResponse:
        ai_generation = (
            self._ai_generation_detector.analyze(image)
            if self._ai_generation_detector is not None
            else AiGenerationResult(
                performed=False,
                status=AnalysisStatus.MODEL_NOT_CONFIGURED,
            )
        )
        manipulation = (
            self._manipulation_detector.analyze(image)
            if self._manipulation_detector is not None
            else ManipulationResult(
                performed=False,
                localization_available=False,
                status=AnalysisStatus.MODEL_NOT_CONFIGURED,
            )
        )
        return ImageAnalysisResponse(
            analysis_version="1",
            ai_generation=ai_generation,
            manipulation=manipulation,
        )


@lru_cache
def get_forensic_service() -> ForensicAnalysisService:
    settings = get_settings()
    return ForensicAnalysisService(
        ai_generation_detector=UniversalFakeDetector(settings),
        manipulation_detector=TruForDetector(settings),
    )
