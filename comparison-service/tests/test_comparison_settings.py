from app.core.comparison_settings import ComparisonSettings


def test_new_environment_name_takes_precedence_over_legacy_alias(
    monkeypatch,
) -> None:
    monkeypatch.setenv("AUTHVAULT_IMAGE_COMPARISON_MAX_IMAGE_BYTES", "2048")
    monkeypatch.setenv("AUTHVAULT_AI_MAX_IMAGE_BYTES", "1024")

    settings = ComparisonSettings(_env_file=None)

    assert settings.max_image_bytes == 2048


def test_legacy_comparison_environment_name_remains_a_fallback(monkeypatch) -> None:
    monkeypatch.setenv("AUTHVAULT_AI_COMPARISON_ORB_MAX_FEATURES", "1234")

    settings = ComparisonSettings(_env_file=None)

    assert settings.orb_max_features == 1234
