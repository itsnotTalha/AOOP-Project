import base64
from functools import lru_cache

import cv2
import numpy as np
from PIL import Image

from app.core.config import Settings
from app.models.schemas import (
    AlignmentResult,
    AlignmentStatus,
    ChangeMaskResult,
    DifferenceResult,
    ImageComparisonResponse,
    ImageComparisonStatus,
)

RANSAC_RANDOM_SEED = 0


class ImageComparisonService:
    """Deterministic known-original alignment and visual-difference processing."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def compare(
        self,
        reference_image: Image.Image,
        target_image: Image.Image,
    ) -> ImageComparisonResponse:
        try:
            reference_rgb = np.asarray(reference_image.convert("RGB"), dtype=np.uint8)
            target_rgb = np.asarray(target_image.convert("RGB"), dtype=np.uint8)
            aligned_target, valid_overlap, alignment = self._align(
                reference_rgb,
                target_rgb,
            )
            return self._calculate_difference(
                reference_rgb,
                aligned_target,
                valid_overlap,
                alignment,
            )
        except (cv2.error, ValueError, MemoryError):
            return self._processing_failed_response()

    def _align(
        self,
        reference_rgb: np.ndarray,
        target_rgb: np.ndarray,
    ) -> tuple[np.ndarray, np.ndarray, AlignmentResult]:
        reference_gray = cv2.cvtColor(reference_rgb, cv2.COLOR_RGB2GRAY)
        target_gray = cv2.cvtColor(target_rgb, cv2.COLOR_RGB2GRAY)
        orb = cv2.ORB_create(nfeatures=self._settings.orb_max_features)
        reference_keypoints, reference_descriptors = orb.detectAndCompute(
            reference_gray,
            None,
        )
        target_keypoints, target_descriptors = orb.detectAndCompute(target_gray, None)
        reference_count = len(reference_keypoints)
        target_count = len(target_keypoints)

        good_matches: list[cv2.DMatch] = []
        homography: np.ndarray | None = None
        inlier_count = 0
        inlier_ratio: float | None = None

        if reference_descriptors is not None and target_descriptors is not None:
            matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=False)
            for neighbors in matcher.knnMatch(target_descriptors, reference_descriptors, k=2):
                if len(neighbors) == 2:
                    best, second = neighbors
                    if best.distance < self._settings.orb_match_ratio * second.distance:
                        good_matches.append(best)

        if len(good_matches) >= 4:
            target_points = np.float32(
                [target_keypoints[match.queryIdx].pt for match in good_matches]
            ).reshape(-1, 1, 2)
            reference_points = np.float32(
                [reference_keypoints[match.trainIdx].pt for match in good_matches]
            ).reshape(-1, 1, 2)
            cv2.setRNGSeed(RANSAC_RANDOM_SEED)
            homography, inlier_mask = cv2.findHomography(
                target_points,
                reference_points,
                cv2.RANSAC,
                self._settings.ransac_reprojection_threshold,
            )
            if inlier_mask is not None:
                inlier_count = int(np.count_nonzero(inlier_mask))
                inlier_ratio = inlier_count / len(good_matches)

        quality_is_sufficient = (
            homography is not None
            and len(good_matches) >= self._settings.min_good_matches
            and inlier_count >= self._settings.min_inliers
            and inlier_ratio is not None
            and inlier_ratio >= self._settings.min_inlier_ratio
        )
        reference_height, reference_width = reference_rgb.shape[:2]

        if quality_is_sufficient:
            aligned_target = cv2.warpPerspective(
                target_rgb,
                homography,
                (reference_width, reference_height),
                flags=cv2.INTER_LINEAR,
                borderMode=cv2.BORDER_CONSTANT,
                borderValue=0,
            )
            target_support = np.full(target_rgb.shape[:2], 255, dtype=np.uint8)
            valid_overlap = cv2.warpPerspective(
                target_support,
                homography,
                (reference_width, reference_height),
                flags=cv2.INTER_NEAREST,
                borderMode=cv2.BORDER_CONSTANT,
                borderValue=0,
            )
            alignment_status = AlignmentStatus.ALIGNED
            method = "ORB_HOMOGRAPHY"
        else:
            aligned_target = cv2.resize(
                target_rgb,
                (reference_width, reference_height),
                interpolation=cv2.INTER_AREA,
            )
            valid_overlap = np.full(
                (reference_height, reference_width),
                255,
                dtype=np.uint8,
            )
            alignment_status = AlignmentStatus.FALLBACK_RESIZE
            method = "RESIZE"

        alignment = AlignmentResult(
            status=alignment_status,
            method=method,
            keypoints_reference=reference_count,
            keypoints_target=target_count,
            good_matches=len(good_matches),
            inliers=inlier_count,
            inlier_ratio=inlier_ratio,
        )
        return aligned_target, valid_overlap, alignment

    def _calculate_difference(
        self,
        reference_rgb: np.ndarray,
        aligned_target_rgb: np.ndarray,
        valid_overlap: np.ndarray,
        alignment: AlignmentResult,
    ) -> ImageComparisonResponse:
        valid = valid_overlap > 0
        comparable_pixels = int(np.count_nonzero(valid))
        if comparable_pixels == 0:
            return ImageComparisonResponse(
                comparison_version="1",
                alignment=alignment,
                difference=DifferenceResult(performed=False),
                mask=ChangeMaskResult(available=False),
                status=ImageComparisonStatus.NO_VALID_OVERLAP,
            )

        blur_size = self._settings.gaussian_blur_kernel_size
        reference_filtered = cv2.GaussianBlur(
            reference_rgb,
            (blur_size, blur_size),
            0,
        )
        target_filtered = cv2.GaussianBlur(
            aligned_target_rgb,
            (blur_size, blur_size),
            0,
        )
        absolute_rgb_difference = cv2.absdiff(reference_filtered, target_filtered)
        scalar_difference = np.mean(absolute_rgb_difference, axis=2)
        mean_absolute_difference = float(np.mean(scalar_difference[valid]))

        threshold_mask = np.where(
            (scalar_difference > self._settings.pixel_difference_threshold) & valid,
            255,
            0,
        ).astype(np.uint8)
        cleaned_mask = self._clean_mask(threshold_mask, valid)
        changed_pixels = int(np.count_nonzero(cleaned_mask))
        changed_area_ratio = changed_pixels / comparable_pixels
        encoded_mask = self._encode_mask(cleaned_mask, reference_rgb.shape[:2])

        return ImageComparisonResponse(
            comparison_version="1",
            alignment=alignment,
            difference=DifferenceResult(
                performed=True,
                changed_area_ratio=changed_area_ratio,
                mean_absolute_difference=mean_absolute_difference,
                structural_similarity=None,
            ),
            mask=ChangeMaskResult(
                available=True,
                format="png",
                base64=base64.b64encode(encoded_mask).decode("ascii"),
            ),
            status=ImageComparisonStatus.COMPLETED,
        )

    def _clean_mask(self, threshold_mask: np.ndarray, valid: np.ndarray) -> np.ndarray:
        kernel_size = self._settings.morphology_kernel_size
        kernel = cv2.getStructuringElement(
            cv2.MORPH_ELLIPSE,
            (kernel_size, kernel_size),
        )
        opened = cv2.morphologyEx(threshold_mask, cv2.MORPH_OPEN, kernel)
        closed = cv2.morphologyEx(opened, cv2.MORPH_CLOSE, kernel)
        closed[~valid] = 0

        component_count, labels, statistics, _ = cv2.connectedComponentsWithStats(
            closed,
            connectivity=8,
        )
        filtered = np.zeros_like(closed)
        for label in range(1, component_count):
            area = int(statistics[label, cv2.CC_STAT_AREA])
            if area >= self._settings.minimum_region_area:
                filtered[labels == label] = 255
        filtered[~valid] = 0
        return filtered

    def _encode_mask(
        self,
        mask: np.ndarray,
        expected_dimensions: tuple[int, int],
    ) -> bytes:
        if mask.shape != expected_dimensions:
            raise ValueError("Change mask dimensions do not match the reference image")
        encoded, buffer = cv2.imencode(".png", mask)
        if not encoded:
            raise ValueError("Could not encode change mask")
        encoded_bytes = buffer.tobytes()
        if len(encoded_bytes) > self._settings.max_mask_bytes:
            raise ValueError("Encoded change mask exceeds the configured size limit")
        return encoded_bytes

    def _processing_failed_response(self) -> ImageComparisonResponse:
        return ImageComparisonResponse(
            comparison_version="1",
            alignment=AlignmentResult(
                status=AlignmentStatus.ALIGNMENT_FAILED,
                keypoints_reference=0,
                keypoints_target=0,
                good_matches=0,
                inliers=0,
            ),
            difference=DifferenceResult(performed=False),
            mask=ChangeMaskResult(available=False),
            status=ImageComparisonStatus.PROCESSING_FAILED,
        )

@lru_cache
def get_image_comparison_service() -> ImageComparisonService:
    from app.core.config import get_settings

    return ImageComparisonService(get_settings())
