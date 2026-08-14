import hashlib
import math
from collections.abc import Callable, Mapping
from pathlib import Path
from threading import BoundedSemaphore
from typing import Any

import torch
from PIL import Image
from torchvision.transforms import Compose, CenterCrop, InterpolationMode, Normalize, Resize, ToTensor

from app.calibration.ufd import UfdCalibrationArtifact, load_calibration_artifact
from app.core.config import Settings
from app.detectors.base import AiGenerationDetector
from app.models.schemas import (
    AiGenerationResult,
    AnalysisStatus,
    CalibrationStatus,
    ModelHealth,
    ModelSignal,
)

MODEL_NAME = "UNIVERSAL_FAKE_DETECT"
MODEL_VERSION = "UFD_CVPR2023_CLIP_VIT_L14_FC_V1"
CLIP_FEATURE_DIMENSION = 768
CLIP_INPUT_SIZE = 224
UFD_RESIZE_SIZE = 256
CLIP_MEAN = (0.48145466, 0.4578275, 0.40821073)
CLIP_STD = (0.26862954, 0.26130258, 0.27577711)
READ_CHUNK_SIZE = 1024 * 1024

ClipLoader = Callable[[Path, str], Any]
ClassifierLoader = Callable[[Path], Mapping[str, Any]]


class UniversalFakeDetector(AiGenerationDetector):
    def __init__(
        self,
        settings: Settings,
        *,
        clip_loader: ClipLoader | None = None,
        classifier_loader: ClassifierLoader | None = None,
        cuda_available: Callable[[], bool] | None = None,
    ) -> None:
        self._settings = settings
        self._clip_loader = clip_loader or _load_local_clip
        self._classifier_loader = classifier_loader or _load_classifier_checkpoint
        self._cuda_available = cuda_available or torch.cuda.is_available
        self._semaphore = BoundedSemaphore(settings.ufd_max_concurrent_inference)
        self._preprocess = build_ufd_preprocess()
        self._clip_model: Any | None = None
        self._classifier: torch.nn.Linear | None = None
        self._device = "cpu"
        self._status = AnalysisStatus.MODEL_NOT_CONFIGURED
        self._calibration_status = CalibrationStatus.NOT_CALIBRATED
        self._calibration: UfdCalibrationArtifact | None = None
        self._clip_checkpoint_sha256: str | None = None
        self._classifier_checkpoint_sha256: str | None = None
        self._configured = settings.ufd_enabled
        self._initialize()

    @property
    def ready(self) -> bool:
        return self._status == AnalysisStatus.COMPLETED

    @property
    def configured(self) -> bool:
        return self._configured

    @property
    def status(self) -> AnalysisStatus:
        return self._status

    @property
    def model_health(self) -> ModelHealth:
        return ModelHealth(
            configured=self.configured,
            ready=self.ready,
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
        )

    @property
    def classifier_checkpoint_sha256(self) -> str | None:
        return self._classifier_checkpoint_sha256

    @property
    def clip_checkpoint_sha256(self) -> str | None:
        return self._clip_checkpoint_sha256

    def analyze(self, image: Image.Image) -> AiGenerationResult:
        if not self.ready:
            return AiGenerationResult(performed=False, status=self._status)
        try:
            raw_logit = self.raw_logit(image)
            raw_score = _sigmoid(raw_logit)
            signal = (
                ModelSignal.SYNTHETIC_LEANING
                if raw_score >= self._settings.ufd_decision_threshold
                else ModelSignal.REAL_LEANING
            )
            calibrated_probability = (
                self._calibration.probability(raw_logit)
                if self._calibration is not None
                else None
            )
            return AiGenerationResult(
                performed=True,
                status=AnalysisStatus.COMPLETED,
                model_name=MODEL_NAME,
                model_version=MODEL_VERSION,
                raw_logit=raw_logit,
                raw_synthetic_score=raw_score,
                decision_threshold=self._settings.ufd_decision_threshold,
                model_signal=signal,
                calibration_status=self._calibration_status,
                calibrated_synthetic_probability=calibrated_probability,
            )
        except Exception:
            return AiGenerationResult(
                performed=False,
                status=AnalysisStatus.PROCESSING_FAILED,
                model_name=MODEL_NAME,
                model_version=MODEL_VERSION,
            )

    def raw_logit(self, image: Image.Image) -> float:
        if not self.ready or self._clip_model is None or self._classifier is None:
            raise RuntimeError("UFD detector is not ready")
        rgb_image = image.convert("RGB")
        try:
            input_tensor = self._preprocess(rgb_image).unsqueeze(0).to(self._device)
        finally:
            if rgb_image is not image:
                rgb_image.close()
        with self._semaphore, torch.inference_mode():
            features = self._clip_model.encode_image(input_tensor).float()
            if features.ndim != 2 or tuple(features.shape) != (1, CLIP_FEATURE_DIMENSION):
                raise ValueError("CLIP returned an unexpected feature shape")
            output = self._classifier(features)
            if tuple(output.shape) != (1, 1):
                raise ValueError("UFD classifier returned an unexpected output shape")
            raw_logit = float(output.item())
        if not math.isfinite(raw_logit):
            raise ValueError("UFD classifier returned a non-finite logit")
        return raw_logit

    def _initialize(self) -> None:
        if not self._settings.ufd_enabled:
            return
        clip_path = self._settings.ufd_clip_checkpoint
        classifier_path = self._settings.ufd_classifier_checkpoint
        if clip_path is None or classifier_path is None:
            return
        if not clip_path.is_file() or not classifier_path.is_file():
            return
        try:
            device = self._resolve_device()
            self._clip_checkpoint_sha256 = sha256_file(clip_path)
            self._classifier_checkpoint_sha256 = sha256_file(classifier_path)
            _require_checksum(
                self._clip_checkpoint_sha256, self._settings.ufd_clip_sha256
            )
            _require_checksum(
                self._classifier_checkpoint_sha256,
                self._settings.ufd_classifier_sha256,
            )
            clip_model = self._clip_loader(clip_path, device)
            self._validate_clip_architecture(clip_model)
            classifier = self._build_classifier(
                self._classifier_loader(classifier_path), device
            )
            clip_model.eval()
            classifier.eval()
            self._clip_model = clip_model
            self._classifier = classifier
            self._device = device
            self._load_calibration()
            self._status = AnalysisStatus.COMPLETED
        except _DeviceUnavailable:
            self._status = AnalysisStatus.DEVICE_UNAVAILABLE
        except Exception:
            self._clip_model = None
            self._classifier = None
            self._status = AnalysisStatus.MODEL_INVALID

    def _resolve_device(self) -> str:
        requested = self._settings.ufd_device
        if requested == "cpu":
            return "cpu"
        if requested == "cuda":
            if not self._cuda_available():
                raise _DeviceUnavailable
            return "cuda"
        return "cuda" if self._cuda_available() else "cpu"

    def _validate_clip_architecture(self, clip_model: Any) -> None:
        if not callable(getattr(clip_model, "encode_image", None)):
            raise ValueError("CLIP model has no image encoder")
        visual = getattr(clip_model, "visual", None)
        output_dimension = getattr(visual, "output_dim", None)
        input_resolution = getattr(visual, "input_resolution", None)
        if output_dimension is None or int(output_dimension) != CLIP_FEATURE_DIMENSION:
            raise ValueError("CLIP image feature dimension is incompatible")
        if input_resolution is None or int(input_resolution) != CLIP_INPUT_SIZE:
            raise ValueError("CLIP input resolution is incompatible")

    def _build_classifier(
        self, checkpoint: Mapping[str, Any], device: str
    ) -> torch.nn.Linear:
        weight, bias = _extract_linear_parameters(checkpoint)
        if tuple(weight.shape) != (1, CLIP_FEATURE_DIMENSION):
            raise ValueError("UFD classifier weight dimensions are incompatible")
        if tuple(bias.shape) != (1,):
            raise ValueError("UFD classifier bias dimensions are incompatible")
        classifier = torch.nn.Linear(CLIP_FEATURE_DIMENSION, 1)
        classifier.load_state_dict(
            {"weight": weight.detach().float(), "bias": bias.detach().float()}
        )
        classifier.to(device)
        with torch.inference_mode():
            test_output = classifier(
                torch.zeros((1, CLIP_FEATURE_DIMENSION), device=device)
            )
        if tuple(test_output.shape) != (1, 1):
            raise ValueError("UFD classifier must produce one logit per image")
        return classifier

    def _load_calibration(self) -> None:
        calibration_path = self._settings.ufd_calibration_path
        if calibration_path is None:
            return
        try:
            artifact = load_calibration_artifact(calibration_path)
            if (
                artifact.model_name != MODEL_NAME
                or artifact.model_version != MODEL_VERSION
                or artifact.model_checkpoint_sha256
                != self._classifier_checkpoint_sha256
                or (
                    artifact.clip_checkpoint_sha256 is not None
                    and artifact.clip_checkpoint_sha256 != self._clip_checkpoint_sha256
                )
            ):
                raise ValueError("calibration does not match the loaded model")
            self._calibration = artifact
            self._calibration_status = CalibrationStatus.CALIBRATED
        except Exception:
            self._calibration = None
            self._calibration_status = CalibrationStatus.CALIBRATION_INVALID


def build_ufd_preprocess() -> Compose:
    return Compose(
        [
            Resize(UFD_RESIZE_SIZE, interpolation=InterpolationMode.BILINEAR),
            CenterCrop(CLIP_INPUT_SIZE),
            ToTensor(),
            Normalize(CLIP_MEAN, CLIP_STD),
        ]
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(READ_CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def _load_local_clip(path: Path, device: str) -> Any:
    import clip

    model, _ = clip.load(str(path), device=device, jit=False)
    return model


def _load_classifier_checkpoint(path: Path) -> Mapping[str, Any]:
    checkpoint = torch.load(path, map_location="cpu", weights_only=True)
    if not isinstance(checkpoint, Mapping):
        raise ValueError("UFD classifier checkpoint must contain a state dictionary")
    for wrapper_key in ("state_dict", "model"):
        wrapped = checkpoint.get(wrapper_key)
        if isinstance(wrapped, Mapping):
            return wrapped
    return checkpoint


def _extract_linear_parameters(
    checkpoint: Mapping[str, Any],
) -> tuple[torch.Tensor, torch.Tensor]:
    weight = _find_parameter(checkpoint, "weight")
    bias = _find_parameter(checkpoint, "bias")
    if not isinstance(weight, torch.Tensor) or not isinstance(bias, torch.Tensor):
        raise ValueError("UFD classifier checkpoint is missing tensor parameters")
    return weight, bias


def _find_parameter(checkpoint: Mapping[str, Any], name: str) -> Any:
    direct = checkpoint.get(name)
    if direct is not None:
        return direct
    matches = [value for key, value in checkpoint.items() if key.endswith(f"fc.{name}")]
    if len(matches) != 1:
        raise ValueError(f"UFD classifier checkpoint has invalid {name} parameters")
    return matches[0]


def _require_checksum(actual: str, expected: str | None) -> None:
    if expected is not None and actual != expected:
        raise ValueError("model asset checksum mismatch")


def _sigmoid(value: float) -> float:
    return torch.sigmoid(torch.tensor(value, dtype=torch.float64)).item()


class _DeviceUnavailable(Exception):
    pass
