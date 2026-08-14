from abc import ABC, abstractmethod

from PIL import Image

from app.models.schemas import AiGenerationResult, ManipulationResult


class AiGenerationDetector(ABC):
    @abstractmethod
    def analyze(self, image: Image.Image) -> AiGenerationResult:
        """Analyze global synthetic-generation evidence for one decoded image."""


class ManipulationDetector(ABC):
    @abstractmethod
    def analyze(self, image: Image.Image) -> ManipulationResult:
        """Analyze and optionally localize manipulation evidence."""
