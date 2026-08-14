import base64
import hashlib
import importlib
import math
import sys
from collections.abc import Callable, Mapping
from concurrent.futures import Future, ThreadPoolExecutor, TimeoutError
from contextlib import contextmanager
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from threading import BoundedSemaphore
from types import SimpleNamespace
from typing import Any, Protocol

import numpy as np
import torch
import torch.nn.functional as functional
from PIL import Image

from app.core.config import Settings
from app.detectors.base import ManipulationDetector
from app.models.schemas import (
    AnalysisStatus,
    ManipulationResult,
    ManipulationSignal,
    ModelHealth,
)

MODEL_NAME = "TRUFOR"
MODEL_VERSION = "TRUFOR_CVPR2023_RELEASED_V1"
PINNED_UPSTREAM_REVISION = "ae54475df6f41a491d7615100feb19263dec13f7"
READ_CHUNK_SIZE = 1024 * 1024


@dataclass(frozen=True)
class RawTruForOutput:
    manipulation_score: float
    anomaly_map: Any
    reliability_map: Any


@dataclass(frozen=True)
class ProcessedTruForOutput:
    manipulation_score: float
    suspicious_area_ratio: float
    reliable_suspicious_area_ratio: float | None
    anomaly_map_png_base64: str
    reliability_map_png_base64: str
    suspicious_mask_png_base64: str


class TruForRuntime(Protocol):
    def infer(self, image: Image.Image) -> RawTruForOutput:
        """Run the administrator-provisioned pinned TruFor runtime."""


RuntimeLoader = Callable[[Path, Path, str], TruForRuntime]


class TruForDetector(ManipulationDetector):
    def __init__(
        self,
        settings: Settings,
        *,
        runtime_loader: RuntimeLoader | None = None,
        cuda_available: Callable[[], bool] | None = None,
    ) -> None:
        self._settings = settings
        self._runtime_loader = runtime_loader or load_official_trufor_runtime
        self._cuda_available = cuda_available or torch.cuda.is_available
        self._runtime: TruForRuntime | None = None
        self._status = AnalysisStatus.MODEL_NOT_CONFIGURED
        self._checkpoint_sha256: str | None = None
        self._configured = settings.trufor_enabled
        self._executor = ThreadPoolExecutor(
            max_workers=settings.trufor_max_concurrent_inference,
            thread_name_prefix="authvault-trufor",
        )
        self._inference_slots = BoundedSemaphore(
            settings.trufor_max_concurrent_inference
        )
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
    def checkpoint_sha256(self) -> str | None:
        return self._checkpoint_sha256

    def analyze(self, image: Image.Image) -> ManipulationResult:
        if not self.ready:
            return self._unavailable_result(self._status)
        timeout = self._settings.trufor_inference_timeout_seconds
        if not self._inference_slots.acquire(timeout=timeout):
            return self._unavailable_result(AnalysisStatus.PROCESSING_TIMEOUT)
        try:
            controlled_image = image.copy()
            future = self._executor.submit(self._run_inference, controlled_image)
        except Exception:
            self._inference_slots.release()
            return self._unavailable_result(AnalysisStatus.PROCESSING_FAILED)
        future.add_done_callback(self._release_inference_slot)
        try:
            processed = future.result(timeout=timeout)
            score = processed.manipulation_score
            signal = (
                ManipulationSignal.ELEVATED_MANIPULATION_SIGNAL
                if score >= self._settings.trufor_score_threshold
                else ManipulationSignal.LOW_MANIPULATION_SIGNAL
            )
            return ManipulationResult(
                performed=True,
                status=AnalysisStatus.COMPLETED,
                model_name=MODEL_NAME,
                model_version=MODEL_VERSION,
                manipulation_score=score,
                decision_threshold=self._settings.trufor_score_threshold,
                model_signal=signal,
                localization_available=True,
                suspicious_area_ratio=processed.suspicious_area_ratio,
                reliable_suspicious_area_ratio=(
                    processed.reliable_suspicious_area_ratio
                ),
                anomaly_map_png_base64=processed.anomaly_map_png_base64,
                reliability_map_png_base64=processed.reliability_map_png_base64,
                suspicious_mask_png_base64=processed.suspicious_mask_png_base64,
            )
        except TimeoutError:
            return self._unavailable_result(AnalysisStatus.PROCESSING_TIMEOUT)
        except Exception:
            return self._unavailable_result(AnalysisStatus.PROCESSING_FAILED)

    def _initialize(self) -> None:
        if not self._settings.trufor_enabled:
            return
        model_path = self._settings.trufor_model_path
        runtime_path = self._settings.trufor_runtime_path
        if model_path is None or runtime_path is None:
            return
        if not model_path.is_file() or not runtime_path.is_dir():
            return
        try:
            if self._settings.trufor_runtime_revision != PINNED_UPSTREAM_REVISION:
                raise ValueError("TruFor runtime revision is not the pinned revision")
            device = self._resolve_device()
            self._checkpoint_sha256 = sha256_file(model_path)
            expected_hash = self._settings.trufor_model_sha256
            if expected_hash is not None and self._checkpoint_sha256 != expected_hash:
                raise ValueError("TruFor model checksum mismatch")
            self._runtime = self._runtime_loader(runtime_path, model_path, device)
            if not callable(getattr(self._runtime, "infer", None)):
                raise ValueError("TruFor runtime has no inference operation")
            self._status = AnalysisStatus.COMPLETED
        except _DeviceUnavailable:
            self._status = AnalysisStatus.DEVICE_UNAVAILABLE
        except Exception:
            self._runtime = None
            self._status = AnalysisStatus.MODEL_INVALID

    def _resolve_device(self) -> str:
        requested = self._settings.trufor_device
        if requested == "cpu":
            return "cpu"
        if requested == "cuda":
            if not self._cuda_available():
                raise _DeviceUnavailable
            return "cuda"
        return "cuda" if self._cuda_available() else "cpu"

    def _run_inference(self, image: Image.Image) -> ProcessedTruForOutput:
        try:
            if self._runtime is None:
                raise RuntimeError("TruFor runtime is unavailable")
            with torch.inference_mode():
                raw_output = self._runtime.infer(image)
            return process_trufor_output(
                raw_output,
                original_size=image.size,
                anomaly_threshold=self._settings.trufor_anomaly_threshold,
                minimum_reliability=self._settings.trufor_min_reliability,
                maximum_map_dimension=self._settings.trufor_max_map_dimension,
                maximum_encoded_bytes=self._settings.trufor_max_map_bytes,
            )
        finally:
            image.close()

    def _release_inference_slot(self, _future: Future[Any]) -> None:
        self._inference_slots.release()

    def _unavailable_result(self, status: AnalysisStatus) -> ManipulationResult:
        return ManipulationResult(
            performed=False,
            status=status,
            model_name=MODEL_NAME if self.configured else None,
            model_version=MODEL_VERSION if self.configured else None,
            localization_available=False,
        )


class OfficialTruForRuntime:
    """Minimal bridge to an administrator-provisioned pinned upstream checkout."""

    def __init__(self, runtime_path: Path, model_path: Path, device: str) -> None:
        self._device = device
        self._model = self._load_model(runtime_path, model_path, device)

    def infer(self, image: Image.Image) -> RawTruForOutput:
        rgb_array = np.asarray(image.convert("RGB"), dtype=np.float32).copy()
        input_tensor = (
            torch.from_numpy(rgb_array.transpose(2, 0, 1))
            .unsqueeze(0)
            .to(self._device)
            / 256.0
        )
        prediction, confidence, detection, _noiseprint = self._model(input_tensor)
        if confidence is None or detection is None:
            raise ValueError("TruFor runtime omitted required outputs")
        anomaly_map = functional.softmax(prediction, dim=1)[0, 1]
        reliability_map = torch.sigmoid(confidence)[0, 0]
        manipulation_score = float(torch.sigmoid(detection).reshape(-1)[0].item())
        return RawTruForOutput(
            manipulation_score=manipulation_score,
            anomaly_map=anomaly_map.detach().cpu(),
            reliability_map=reliability_map.detach().cpu(),
        )

    def _load_model(self, runtime_path: Path, model_path: Path, device: str) -> Any:
        _require_pinned_checkout(runtime_path)
        required_files = (
            runtime_path / "models" / "cmx" / "builder_np_conf.py",
            runtime_path / "models" / "DnCNN.py",
        )
        if any(not path.is_file() for path in required_files):
            raise ValueError("TruFor runtime source is incomplete")
        with _temporary_import_path(runtime_path):
            builder = importlib.import_module("models.cmx.builder_np_conf")
        builder_file = Path(builder.__file__).resolve()
        if not builder_file.is_relative_to(runtime_path.resolve()):
            raise ValueError("Unexpected TruFor runtime module was loaded")
        model_class = getattr(builder, "myEncoderDecoder", None)
        if model_class is None:
            raise ValueError("TruFor model builder is unavailable")
        model = model_class(cfg=_official_runtime_config())
        checkpoint = torch.load(model_path, map_location="cpu", weights_only=True)
        if not isinstance(checkpoint, Mapping):
            raise ValueError("TruFor checkpoint is invalid")
        state_dict = checkpoint.get("state_dict")
        if not isinstance(state_dict, Mapping):
            raise ValueError("TruFor checkpoint has no state dictionary")
        model.load_state_dict(state_dict, strict=True)
        model.to(device)
        model.eval()
        return model


def load_official_trufor_runtime(
    runtime_path: Path,
    model_path: Path,
    device: str,
) -> TruForRuntime:
    return OfficialTruForRuntime(runtime_path, model_path, device)


def process_trufor_output(
    raw_output: RawTruForOutput,
    *,
    original_size: tuple[int, int],
    anomaly_threshold: float,
    minimum_reliability: float,
    maximum_map_dimension: int,
    maximum_encoded_bytes: int,
) -> ProcessedTruForOutput:
    width, height = original_size
    if width <= 0 or height <= 0:
        raise ValueError("original image dimensions are invalid")
    score = float(raw_output.manipulation_score)
    if not math.isfinite(score) or not 0.0 <= score <= 1.0:
        raise ValueError("TruFor manipulation score is invalid")
    anomaly = _normalize_map(raw_output.anomaly_map, width, height)
    reliability = _normalize_map(raw_output.reliability_map, width, height)

    suspicious = anomaly >= anomaly_threshold
    reliable = reliability >= minimum_reliability
    suspicious_area_ratio = float(suspicious.float().mean().item())
    reliable_count = int(reliable.sum().item())
    reliable_suspicious_area_ratio = (
        float((suspicious & reliable).sum().item() / reliable_count)
        if reliable_count > 0
        else None
    )
    binary_mask = (suspicious & reliable).float()

    visual_size = _bounded_visual_size(width, height, maximum_map_dimension)
    anomaly_png, anomaly_base64 = _encode_map(anomaly, visual_size, binary=False)
    reliability_png, reliability_base64 = _encode_map(
        reliability, visual_size, binary=False
    )
    mask_png, mask_base64 = _encode_map(binary_mask, visual_size, binary=True)
    if len(anomaly_png) + len(reliability_png) + len(mask_png) > maximum_encoded_bytes:
        raise ValueError("TruFor visualization output exceeds its safe limit")

    return ProcessedTruForOutput(
        manipulation_score=score,
        suspicious_area_ratio=suspicious_area_ratio,
        reliable_suspicious_area_ratio=reliable_suspicious_area_ratio,
        anomaly_map_png_base64=anomaly_base64,
        reliability_map_png_base64=reliability_base64,
        suspicious_mask_png_base64=mask_base64,
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(READ_CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def _normalize_map(value: Any, width: int, height: int) -> torch.Tensor:
    result = torch.as_tensor(value, dtype=torch.float32).detach().cpu()
    while result.ndim > 2:
        if result.shape[0] != 1:
            raise ValueError("TruFor map dimensions are invalid")
        result = result[0]
    if result.ndim != 2 or result.numel() == 0:
        raise ValueError("TruFor map dimensions are invalid")
    if not torch.isfinite(result).all():
        raise ValueError("TruFor map contains non-finite values")
    result = result.clamp(0.0, 1.0)
    if tuple(result.shape) != (height, width):
        result = functional.interpolate(
            result.unsqueeze(0).unsqueeze(0),
            size=(height, width),
            mode="bilinear",
            align_corners=False,
        )[0, 0]
    if tuple(result.shape) != (height, width) or not torch.isfinite(result).all():
        raise ValueError("TruFor map could not be mapped to the original image")
    return result


def _bounded_visual_size(width: int, height: int, maximum_dimension: int) -> tuple[int, int]:
    scale = min(1.0, maximum_dimension / max(width, height))
    return max(1, round(width * scale)), max(1, round(height * scale))


def _encode_map(
    value: torch.Tensor,
    output_size: tuple[int, int],
    *,
    binary: bool,
) -> tuple[bytes, str]:
    array = np.rint(value.numpy() * 255.0).astype(np.uint8)
    image = Image.fromarray(array, mode="L")
    try:
        if image.size != output_size:
            interpolation = Image.Resampling.NEAREST if binary else Image.Resampling.BILINEAR
            resized = image.resize(output_size, interpolation)
            image.close()
            image = resized
        output = BytesIO()
        image.save(output, format="PNG", optimize=True)
        png = output.getvalue()
    finally:
        image.close()
    return png, base64.b64encode(png).decode("ascii")


def _official_runtime_config() -> SimpleNamespace:
    extra = _AttributeConfig(
        BACKBONE="mit_b2",
        DECODER="MLPDecoder",
        DECODER_EMBED_DIM=512,
        PREPRC="imagenet",
        BN_EPS=0.001,
        BN_MOMENTUM=0.1,
        DETECTION="confpool",
        CONF=True,
    )
    model = SimpleNamespace(EXTRA=extra, MODS=("RGB", "NP++"), PRETRAINED="")
    dataset = SimpleNamespace(NUM_CLASSES=2)
    return SimpleNamespace(MODEL=model, DATASET=dataset)


def _require_pinned_checkout(runtime_path: Path) -> None:
    resolved_runtime = runtime_path.resolve()
    repository_root: Path | None = None
    for candidate in (resolved_runtime, *resolved_runtime.parents):
        if (candidate / ".git").is_dir():
            repository_root = candidate
            break
    if repository_root is None:
        raise ValueError("TruFor runtime must be an exact pinned Git checkout")
    revision = _read_git_head(repository_root / ".git")
    if revision != PINNED_UPSTREAM_REVISION:
        raise ValueError("TruFor runtime checkout does not match the pinned revision")


def _read_git_head(git_directory: Path) -> str:
    try:
        head = (git_directory / "HEAD").read_text(encoding="ascii").strip()
        if not head.startswith("ref: "):
            return head.lower()
        reference = head.removeprefix("ref: ").strip()
        loose_reference = git_directory / reference
        if loose_reference.is_file():
            return loose_reference.read_text(encoding="ascii").strip().lower()
        packed_refs = git_directory / "packed-refs"
        for line in packed_refs.read_text(encoding="ascii").splitlines():
            if line and not line.startswith(("#", "^")):
                revision, name = line.split(" ", maxsplit=1)
                if name == reference:
                    return revision.lower()
    except (OSError, UnicodeError, ValueError):
        pass
    raise ValueError("TruFor runtime revision could not be verified")


class _AttributeConfig(dict[str, Any]):
    def __getattr__(self, name: str) -> Any:
        try:
            return self[name]
        except KeyError as exception:
            raise AttributeError(name) from exception


@contextmanager
def _temporary_import_path(path: Path):
    path_text = str(path.resolve())
    sys.path.insert(0, path_text)
    try:
        yield
    finally:
        try:
            sys.path.remove(path_text)
        except ValueError:
            pass


class _DeviceUnavailable(Exception):
    pass
