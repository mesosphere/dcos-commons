import pytest
from .package_builder import UniversePackageBuilder
from .package import Package

def test_non_existent_input_dir_raises_exception():
    with pytest.raises(Exception) as e:
        UniversePackageBuilder(None, '__SHOULD_NOT_EXIST__', '.', [])

    assert "Provided package path is not a directory: __SHOULD_NOT_EXIST__" in str(e.value)


def test_empty_input_dir_raises_exception():
    with pytest.raises(Exception) as e:
        UniversePackageBuilder(None, 'resources/empty', '.', [])

    assert "Provided package path does not contain the expected package files: resources/empty" in str(e.value)


def test_template_service_(mocker):

    package = Package("template", "stub-universe")
    package.get_upgrades_from = mocker.MagicMock(return_value="1.2.3")

    upb = UniversePackageBuilder(package, 'resources/template', ',', [])

    template_mapping = upb._get_template_mapping_for_content("")
    assert 'upgrades-from' in template_mapping
    assert template_mapping['upgrades-from'] == "1.2.3"
