import argparse
import math
from pathlib import Path

from PIL import Image, ImageOps, UnidentifiedImageError

from app.calibration.ufd import (
    brier_score,
    create_calibration_artifact,
    fit_platt_calibration,
    save_calibration_artifact,
)
from app.core.config import get_settings
from app.detectors.universal_fake_detector import UniversalFakeDetector

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fit an offline Platt calibration artifact for UFD logits."
    )
    parser.add_argument("--real-dir", type=Path, required=True)
    parser.add_argument("--synthetic-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dataset-id", required=True)
    parser.add_argument("--minimum-samples", type=int)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    settings = get_settings()
    minimum_samples = (
        args.minimum_samples
        if args.minimum_samples is not None
        else settings.ufd_minimum_calibration_samples
    )
    if minimum_samples < 2:
        parser.error("--minimum-samples must be at least 2")
    if args.output.exists() and not args.overwrite:
        parser.error("output exists; pass --overwrite to replace it")

    detector = UniversalFakeDetector(settings)
    if not detector.ready:
        parser.error(f"UFD model is not ready: {detector.status.value}")

    real_files = _image_files(args.real_dir)
    synthetic_files = _image_files(args.synthetic_dir)
    if not real_files or not synthetic_files:
        parser.error("both real and synthetic directories must contain supported images")

    logits: list[float] = []
    labels: list[int] = []
    for label, files in ((0, real_files), (1, synthetic_files)):
        for image_path in files:
            try:
                with Image.open(image_path) as source:
                    image = ImageOps.exif_transpose(source).convert("RGB")
                    image.load()
                try:
                    logits.append(detector.raw_logit(image))
                    labels.append(label)
                finally:
                    image.close()
            except (OSError, SyntaxError, ValueError, UnidentifiedImageError) as exception:
                parser.error(f"could not process calibration image: {image_path.name}")

    try:
        a, b = fit_platt_calibration(logits, labels, minimum_samples)
        classifier_hash = detector.classifier_checkpoint_sha256
        clip_hash = detector.clip_checkpoint_sha256
        if classifier_hash is None or clip_hash is None:
            raise ValueError("model checkpoint hashes are unavailable")
        artifact = create_calibration_artifact(
            a=a,
            b=b,
            labels=labels,
            dataset_id=args.dataset_id.strip(),
            model_name="UNIVERSAL_FAKE_DETECT",
            model_version="UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
            model_checkpoint_sha256=classifier_hash,
            clip_checkpoint_sha256=clip_hash,
        )
        save_calibration_artifact(artifact, args.output, overwrite=args.overwrite)
    except (ValueError, FileExistsError) as exception:
        parser.error(str(exception))

    raw_probabilities = [_sigmoid(value) for value in logits]
    calibrated_probabilities = [artifact.probability(value) for value in logits]
    print(f"sampleCount={len(logits)}")
    print(f"realCount={labels.count(0)}")
    print(f"syntheticCount={labels.count(1)}")
    print(f"brierBefore={brier_score(raw_probabilities, labels):.8f}")
    print(f"brierAfter={brier_score(calibrated_probabilities, labels):.8f}")
    print(f"output={args.output}")


def _image_files(directory: Path) -> list[Path]:
    if not directory.is_dir():
        raise SystemExit(f"calibration directory is unavailable: {directory}")
    return sorted(
        (
            path
            for path in directory.rglob("*")
            if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
        ),
        key=lambda path: path.as_posix(),
    )


def _sigmoid(value: float) -> float:
    if value >= 0:
        return 1.0 / (1.0 + math.exp(-value))
    exponential = math.exp(value)
    return exponential / (1.0 + exponential)


if __name__ == "__main__":
    main()
