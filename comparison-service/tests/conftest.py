from collections.abc import Iterator
from pathlib import Path

import pytest

from app.core.comparison_settings import get_comparison_settings
from app.services.image_comparison_service import get_image_comparison_service


@pytest.fixture(autouse=True)
def isolate_local_environment_file(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> Iterator[None]:
    monkeypatch.chdir(tmp_path)
    get_comparison_settings.cache_clear()
    get_image_comparison_service.cache_clear()
    yield
    get_comparison_settings.cache_clear()
    get_image_comparison_service.cache_clear()
