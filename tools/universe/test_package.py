from .package import Package
from .package import Version


# def test_no_version_resolver_upgrades_from_all(mocker):

#     version_resolver = mocker.MagicMock()
#     version_resolver.get_latest_version.return_value = None

#     package = Package('name', 'version', version_resolver=version_resolver)

#     assert package.get_upgrades_from() == '*'
#     version_resolver.get_latest_version.assert_called_once()


# def test_upgrades_from_all_returns_latest_version(mocker):
#     version_resolver = mocker.Mock()
#     version_resolver.get_latest_version.return_value = "1.2.3"

#     package = Package('name', 'version', version_resolver=version_resolver)

#     assert package.get_upgrades_from() == "1.2.3"
#     version_resolver.get_latest_version.assert_called_once()


# def test_downgrades_to_equals_upgrades_from(mocker):
#     version_resolver = mocker.Mock()
#     version_resolver.get_latest_version.return_value = "1.2.3"

#     package = Package('name', 'version', version_resolver=version_resolver)

#     assert package.get_downgrades_to() == package.get_upgrades_from()


def test_version_comparison():
    assert Version(0, "1.2.4") < Version(10, "1.2.4")
    assert Version(0, "1.2.5") < Version(10, "1.2.4")
    assert Version(0, "1.2.3") < Version(0, "1.2.4")

    assert Version(0, "1.2.3") == Version(0, "1.2.3")

    assert Version(10, "1.2.4") > Version(0, "1.2.4")
    assert Version(10, "1.2.4") > Version(0, "1.2.5")
    assert Version(0, "1.2.4") > Version(0, "1.2.3")


def test_package_from_json():
    package_json = {
                    'name': 'package',
                    'version': '1.2.3',
                    'releaseVersion': 10
                    }
    p = Package.from_json(package_json)

    assert p.get_name() == package_json['name']
    assert p.get_version() == Version(10, '1.2.3')
