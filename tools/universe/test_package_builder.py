import pytest
from .package_builder import UniversePackageBuilder
from .package import Package, Version

from pytest_mock import MockFixture


def test_non_existent_input_dir_raises_exception() -> None:
    with pytest.raises(Exception) as e:
        UniversePackageBuilder(None, None, "__SHOULD_NOT_EXIST__", ".", [])

    assert "Provided package path is not a directory: __SHOULD_NOT_EXIST__" in str(e.value)


def test_empty_input_dir_raises_exception() -> None:
    with pytest.raises(Exception) as e:
        UniversePackageBuilder(
            package=None,
            package_manager=None,
            input_dir_path="resources/empty",
            upload_dir_uri=".",
            artifact_paths=[],
        )

    assert (
        "Provided package path does not contain the expected package files: resources/empty"
        in str(e.value)
    )


def test_template_service_(mocker: MockFixture) -> None:

    package_json = {"name": "template", "version": "1.2.3", "releaseVersion": 0}
    package = Package("template", Version("1.2.3", "1.2.3"))
    package_manager = mocker.Mock()

    package_manager.get_latest = mocker.MagicMock(return_value=Package.from_json(package_json))

    upb = UniversePackageBuilder(package, package_manager, "resources/template", ",", [])

    template_mapping = upb._get_template_mapping_for_content("")
    assert "upgrades-from" in template_mapping
    assert template_mapping["upgrades-from"] == "1.2.3"
