from .package import Package


def test_no_version_resolver_upgrades_from_all(mocker):

    version_resolver = mocker.MagicMock()
    version_resolver.get_latest_version.return_value = None

    package = Package('name', 'version', version_resolver=version_resolver)

    assert package.get_upgrades_from() == '*'
    version_resolver.get_latest_version.assert_called_once()


def test_upgrades_from_all_returns_latest_version(mocker):
    version_resolver = mocker.Mock()
    version_resolver.get_latest_version.return_value = "1.2.3"

    package = Package('name', 'version', version_resolver=version_resolver)

    assert package.get_upgrades_from() == "1.2.3"
    version_resolver.get_latest_version.assert_called_once()


def test_downgrades_to_equals_upgrades_from(mocker):
    version_resolver = mocker.Mock()
    version_resolver.get_latest_version.return_value = "1.2.3"

    package = Package('name', 'version', version_resolver=version_resolver)

    assert package.get_downgrades_to() == package.get_upgrades_from()
